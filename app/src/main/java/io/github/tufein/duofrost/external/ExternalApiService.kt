package io.github.tufein.duofrost.external

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

/**
 * Bound-service API (v2). Binding is the only entry point that exposes a
 * trustworthy caller UID: [Binder.getCallingUid] inside [onBind] belongs to the
 * connecting app, whereas a broadcast always reports this process.
 *
 * A client is only given a binder when the external API master toggle is on and
 * its package is explicitly approved. Commands arrive as [ExternalApi.MSG_COMMAND]
 * messages whose data bundle carries the usual action/extras; results are
 * returned to `msg.replyTo` as [ExternalApi.MSG_RESULT] with `arg1` = result code.
 */
class ExternalApiService : Service() {

    companion object {
        private const val TAG = "BIBI/ExtApiSvc"
    }

    override fun onBind(intent: Intent?): IBinder? {
        val callerUid = Binder.getCallingUid()
        val callerPackage = ExternalApiDispatcher.resolveCallerPackage(this, callerUid)
            ?: return refuse("unresolvable caller uid $callerUid")
        if (!ExternalApiGate.isAllowed(this, callerPackage)) {
            if (ExternalApiGate.isMasterEnabled(
                    getSharedPreferences(ExternalApiGate.PREFS_NAME, MODE_PRIVATE)
                )
            ) {
                // Surface the waiting app in the approval dialog, even when
                // Android's package visibility would hide it from a query.
                ExternalApiGate.recordPendingRequest(this, callerPackage)
            }
            return refuse("$callerPackage is not an approved external API caller")
        }

        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what != ExternalApi.MSG_COMMAND) return
                val requestId = msg.data?.getString(ExternalApi.EXTRA_REQUEST_ID)
                val (code, data) = runCatching {
                    handleCommand(callerUid, callerPackage, msg.data)
                }.getOrElse {
                    Log.w(TAG, "handleCommand failed for $callerPackage", it)
                    ExternalApi.RESULT_REJECTED_VALIDATION to null
                }
                reply(msg, requestId, code, data)
            }
        }
        return Messenger(handler).binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    private fun handleCommand(
        callerUid: Int,
        callerPackage: String,
        data: Bundle?
    ): Pair<Int, String?> {
        if (data == null) {
            return ExternalApi.RESULT_REJECTED_VALIDATION to null
        }
        val action = data.getString(ExternalApi.EXTRA_ACTION)
            ?: return ExternalApi.RESULT_REJECTED_UNKNOWN_ACTION to null

        // Re-check per command: the user may revoke approval while connected.
        if (!ExternalApiGate.isAllowed(this, callerPackage)) {
            return ExternalApi.RESULT_REJECTED_DISABLED to null
        }

        val intent = Intent().apply {
            this.action = action
            putExtras(data)
        }

        if (action == ExternalApi.ACTION_QUERY_PLUGIN) {
            return ExternalApiDispatcher.queryPlugin(this, intent)
        }

        if (action == ExternalApi.ACTION_PULSE) {
            val kind = ExternalApiDispatcher.validatedPulseKind(
                intent.getStringExtra(ExternalApi.EXTRA_PULSE_KIND)
            ) ?: return ExternalApi.RESULT_REJECTED_VALIDATION to null
            if (!ExternalApiRateLimiters.pulses.allow(callerUid)) {
                return ExternalApi.RESULT_REJECTED_RATE_LIMITED to null
            }
            val result = ExternalApiDispatcher.dispatchPulse(this, kind)
            return ExternalApiDispatcher.resultCodeFor(result) to null
        }

        if (!ExternalApiRateLimiters.commands.allow(callerUid)) {
            return ExternalApi.RESULT_REJECTED_RATE_LIMITED to null
        }

        val cmd = ExternalCommandValidator.validate(intent, callerPackage).getOrElse { error ->
            Log.d(TAG, "rejected $callerPackage: ${error.message}")
            return ExternalApiDispatcher.resultCodeFor(error) to null
        }
        val result = ExternalApiDispatcher.dispatch(this, cmd)
        return ExternalApiDispatcher.resultCodeFor(result) to cmd.requestId
    }

    private fun reply(msg: Message, requestId: String?, code: Int, data: String?) {
        val replyTo = msg.replyTo ?: return
        val reply = Message.obtain(null, ExternalApi.MSG_RESULT).apply {
            arg1 = code
            this.data = Bundle().apply {
                putString(ExternalApi.EXTRA_REQUEST_ID, requestId)
                putString(ExternalApi.EXTRA_RESULT_DATA, data)
            }
        }
        runCatching { replyTo.send(reply) }
            .onFailure { Log.w(TAG, "reply failed", it) }
    }

    private fun refuse(reason: String): IBinder? {
        Log.d(TAG, "bind refused: $reason")
        return null
    }
}

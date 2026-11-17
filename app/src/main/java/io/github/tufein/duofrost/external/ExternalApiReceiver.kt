package io.github.tufein.duofrost.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log

/**
 * Legacy broadcast entry point. It stays available only when the user enables
 * the legacy option: a BroadcastReceiver cannot recover the sending app, so
 * every command resolves to this process's own UID. New companions should use
 * [ExternalApiService] and the approved-package list instead.
 */
class ExternalApiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BIBI/ExtApi"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            handleSafely(context, intent)
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive: unhandled error", t)
            setResult(ExternalApi.RESULT_REJECTED_VALIDATION, null)
        }
    }

    private fun handleSafely(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
        if (!ExternalApiGate.isMasterEnabled(prefs) ||
            !ExternalApiGate.isLegacyBroadcastEnabled(prefs)
        ) {
            setResult(ExternalApi.RESULT_REJECTED_DISABLED, null)
            return
        }

        val callerUid = Binder.getCallingUid()
        val callerPackage = ExternalApiDispatcher.resolveCallerPackage(context, callerUid)
        if (callerPackage == null) {
            setResult(ExternalApi.RESULT_REJECTED_UNAUTHORIZED, null)
            return
        }
        // Legacy callers cannot be authenticated; they are attributed to this
        // process because getCallingUid() inside a receiver reports our own UID.
        Log.d(TAG, "legacy ${intent.action} attributed to $callerPackage (uid=$callerUid)")

        if (intent.action == ExternalApi.ACTION_QUERY_PLUGIN) {
            val (code, data) = ExternalApiDispatcher.queryPlugin(context, intent)
            setResult(code, data)
            return
        }

        if (intent.action == ExternalApi.ACTION_PULSE) {
            val kind = ExternalApiDispatcher.validatedPulseKind(
                intent.getStringExtra(ExternalApi.EXTRA_PULSE_KIND)
            )
            if (kind == null) {
                setResult(ExternalApi.RESULT_REJECTED_VALIDATION, null)
                return
            }
            if (!ExternalApiRateLimiters.pulses.allow(callerUid)) {
                setResult(ExternalApi.RESULT_REJECTED_RATE_LIMITED, null)
                return
            }
            val result = ExternalApiDispatcher.dispatchPulse(context, kind)
            setResult(ExternalApiDispatcher.resultCodeFor(result), null)
            return
        }

        if (!ExternalApiRateLimiters.commands.allow(callerUid)) {
            setResult(ExternalApi.RESULT_REJECTED_RATE_LIMITED, null)
            return
        }

        val cmd = ExternalCommandValidator.validate(intent, callerPackage).getOrElse { error ->
            Log.d(TAG, "rejected $callerPackage: ${error.message}")
            setResult(ExternalApiDispatcher.resultCodeFor(error), null)
            return
        }
        val result = ExternalApiDispatcher.dispatch(context, cmd)
        setResult(ExternalApiDispatcher.resultCodeFor(result), cmd.requestId)
    }

    private fun setResult(code: Int, data: String?) {
        if (isOrderedBroadcast) {
            resultCode = code
            resultData = data
        }
    }
}

package io.github.tufein.duofrost.external

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.tufein.duofrost.plugins.PluginPrefs
import io.github.tufein.duofrost.services.LEDService

internal enum class ExternalDispatchResult { ACCEPTED, UNAVAILABLE, REJECTED }

/**
 * Shared command execution for the bound-service API and the opt-in legacy
 * broadcast receiver. Callers supply an already authenticated package name;
 * this object performs no identity checks of its own.
 */
internal object ExternalApiDispatcher {

    private const val TAG = "BIBI/ExtApi"

    fun resolveCallerPackage(context: Context, uid: Int): String? {
        if (uid <= 0) return null
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    /** Returns the canonical pulse kind, or null when the caller sent nonsense. */
    fun validatedPulseKind(raw: String?): String? = when (raw) {
        null, ExternalApi.PULSE_KIND_PULSE -> ExternalApi.PULSE_KIND_PULSE
        ExternalApi.PULSE_KIND_STATIC -> ExternalApi.PULSE_KIND_STATIC
        else -> null
    }

    fun dispatch(context: Context, cmd: ExternalApiCommand): ExternalDispatchResult = when (cmd) {
        is ExternalApiCommand.Display -> {
            val delivered = forward(context, LEDService.ACTION_EXTERNAL_DISPLAY) {
                putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
                putExtra(LEDService.EXTRA_EXTERNAL_EFFECT, cmd.effect.name)
                putExtra(LEDService.EXTRA_EXTERNAL_COLOR, cmd.color)
                putExtra(LEDService.EXTRA_EXTERNAL_COLOR_RIGHT, cmd.colorRight)
                putExtra(LEDService.EXTRA_EXTERNAL_INTENSITY, cmd.intensity)
                putExtra(LEDService.EXTRA_EXTERNAL_SPEED, cmd.speed)
                putExtra(LEDService.EXTRA_EXTERNAL_SMOOTHNESS, cmd.smoothness)
                putExtra(LEDService.EXTRA_EXTERNAL_SENSITIVITY, cmd.sensitivity)
                putExtra(LEDService.EXTRA_EXTERNAL_PRIORITY, cmd.priority)
                putExtra(LEDService.EXTRA_EXTERNAL_PHASE_SECONDS, cmd.phaseSeconds)
                putExtra(LEDService.EXTRA_EXTERNAL_FLICKERING, cmd.flickering)
                putExtra(LEDService.EXTRA_EXTERNAL_BURST_WALL_MS, cmd.burstWallMs)
                when (val t = cmd.terminator) {
                    is Terminator.Duration -> {
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_DURATION)
                        putExtra(LEDService.EXTRA_EXTERNAL_DURATION_MS, t.millis)
                    }
                    Terminator.UntilNextCommand ->
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_NEXT_COMMAND)
                    Terminator.UntilExplicitClear ->
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_EXPLICIT_CLEAR)
                }
            }
            if (delivered) ExternalDispatchResult.ACCEPTED else ExternalDispatchResult.UNAVAILABLE
        }
        is ExternalApiCommand.Clear -> {
            val delivered = forward(context, LEDService.ACTION_EXTERNAL_CLEAR) {
                putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
            }
            if (delivered) ExternalDispatchResult.ACCEPTED else ExternalDispatchResult.UNAVAILABLE
        }
        is ExternalApiCommand.InstallProfile -> {
            val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
            if (ExternalProfileStore.installManagedPreset(prefs, cmd)) {
                ExternalDispatchResult.ACCEPTED
            } else {
                ExternalDispatchResult.REJECTED
            }
        }
        is ExternalApiCommand.UninstallProfile -> {
            val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
            if (ExternalProfileStore.uninstallManagedPreset(prefs, cmd.callerPackage, cmd.profileName)) {
                ExternalDispatchResult.ACCEPTED
            } else {
                ExternalDispatchResult.REJECTED
            }
        }
    }

    fun dispatchPulse(context: Context, kind: String): ExternalDispatchResult {
        val delivered = forward(context, LEDService.ACTION_EXTERNAL_PULSE) {
            putExtra(LEDService.EXTRA_EXTERNAL_PULSE_KIND, kind)
        }
        return if (delivered) ExternalDispatchResult.ACCEPTED else ExternalDispatchResult.UNAVAILABLE
    }

    fun queryPlugin(context: Context, intent: Intent): Pair<Int, String?> {
        val apiVersion = intent.getIntExtra(ExternalApi.EXTRA_API_VERSION, ExternalApi.API_VERSION)
        if (apiVersion != ExternalApi.API_VERSION) {
            return ExternalApi.RESULT_REJECTED_VERSION to null
        }
        val pluginId = intent.getStringExtra(ExternalApi.EXTRA_PLUGIN_ID)?.trim()
        if (pluginId.isNullOrEmpty()) {
            return ExternalApi.RESULT_REJECTED_VALIDATION to null
        }
        val installedVersion = PluginPrefs.installedVersion(PluginPrefs.prefs(context), pluginId)
        return if (installedVersion != null && installedVersion > 0) {
            ExternalApi.RESULT_ACCEPTED to "$pluginId:$installedVersion"
        } else {
            ExternalApi.RESULT_NOT_FOUND to pluginId
        }
    }

    fun resultCodeFor(error: Throwable): Int = when (
        (error as? ExternalCommandValidator.ValidationException)?.kind
    ) {
        ExternalCommandValidator.ErrorKind.UnsupportedVersion -> ExternalApi.RESULT_REJECTED_VERSION
        ExternalCommandValidator.ErrorKind.UnknownAction -> ExternalApi.RESULT_REJECTED_UNKNOWN_ACTION
        else -> ExternalApi.RESULT_REJECTED_VALIDATION
    }

    fun resultCodeFor(result: ExternalDispatchResult): Int = when (result) {
        ExternalDispatchResult.ACCEPTED -> ExternalApi.RESULT_ACCEPTED
        ExternalDispatchResult.UNAVAILABLE -> ExternalApi.RESULT_REJECTED_UNAVAILABLE
        ExternalDispatchResult.REJECTED -> ExternalApi.RESULT_REJECTED_CONFLICT
    }

    /**
     * Only forward commands if the lighting service is already live. External
     * callers can't start a foreground service from the background on Android
     * 12+, and reporting success while nothing happened misleads companions.
     */
    private fun forward(
        context: Context,
        action: String,
        fill: Intent.() -> Unit
    ): Boolean {
        if (!LEDService.isRunning) {
            Log.d(TAG, "forward: service not running, dropping $action")
            return false
        }
        val svc = Intent(context, LEDService::class.java).apply {
            this.action = action
            fill()
        }
        return runCatching { context.startService(svc) }
            .onFailure { Log.w(TAG, "forward: startService failed", it) }
            .isSuccess
    }
}

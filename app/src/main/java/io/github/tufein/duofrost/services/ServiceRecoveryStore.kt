package io.github.tufein.duofrost.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import io.github.tufein.duofrost.schedule.ScheduleAction
import io.github.tufein.duofrost.schedule.ScheduleEvaluator
import io.github.tufein.duofrost.schedule.ScheduleStore
import java.time.ZonedDateTime

object ServiceRecoveryStore {
    const val PREF_KEEP_RUNNING = "keep_running_enabled"
    private const val STATE_PREFS = "duofrost_service_state"
    private const val CONFIGURATION = "configuration"
    private const val DESIRED_RUNNING = "desired_running"
    private const val MUTED = "output_muted"
    private const val TAG = "DuoFrostRecovery"

    private fun state(context: Context): SharedPreferences =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    fun isKeepRunningEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_KEEP_RUNNING, true)

    fun setKeepRunningEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEEP_RUNNING, enabled).apply()
    }

    fun isDesiredRunning(context: Context): Boolean = state(context).getBoolean(DESIRED_RUNNING, false)

    fun isMuted(context: Context): Boolean = state(context).getBoolean(MUTED, false)

    fun setMuted(context: Context, muted: Boolean) {
        state(context).edit().putBoolean(MUTED, muted).commit()
    }

    fun markStopped(context: Context) {
        val stored = state(context)
        if (stored.getBoolean(DESIRED_RUNNING, false) || stored.getBoolean(MUTED, false)) {
            stored.edit().putBoolean(DESIRED_RUNNING, false).remove(MUTED).commit()
        }
        SleepTimerStore.cancel(context)
    }

    fun recordStarted(context: Context, intent: Intent) {
        if (intent.action != null) return
        val stored = state(context)
        val configuration = ServiceRecoveryCodec.snapshot(configurationFrom(intent))
        if (configuration == null) {
            Log.w(TAG, "Invalid start configuration; automatic restoration disabled")
            stored.edit().remove(CONFIGURATION).putBoolean(DESIRED_RUNNING, false).commit()
            return
        }
        // Start and explicit Stop are rare lifecycle actions; commit ensures a
        // process killed immediately afterwards observes the intended state.
        stored.edit()
            .putString(CONFIGURATION, ServiceRecoveryCodec.encode(configuration))
            .putBoolean(DESIRED_RUNNING, true)
            .commit()
    }

    fun mergeUpdate(context: Context, intent: Intent) {
        if (intent.action != LEDService.ACTION_UPDATE_PARAMS) return
        val stored = state(context)
        val raw = stored.getString(CONFIGURATION, null)
        val current = ServiceRecoveryCodec.decode(raw) ?: return
        val merged = ServiceRecoveryCodec.merge(current, configurationFrom(intent)) ?: return
        if (current == merged) return
        // No per-frame work: only parameter changes update this small snapshot.
        stored.edit().putString(CONFIGURATION, ServiceRecoveryCodec.encode(merged)).apply()
    }

    fun buildLastConfigurationIntent(context: Context, prefs: SharedPreferences): Intent? {
        val configuration = ServiceRecoveryCodec.decode(state(context).getString(CONFIGURATION, null)) ?: return null
        return Intent(context, LEDService::class.java).apply {
            configuration.values.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Float -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                }
            }
            applyCurrentGlobalSettings(this, prefs)
        }
    }

    fun restoreIntent(
        context: Context,
        prefs: SharedPreferences,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Intent? {
        if (SleepTimerStore.expireIfDue(context)) return null
        val decision = ServiceRecoveryPolicy.decide(
            ServiceRecoveryPolicy.Signal.STICKY_RESTART,
            autoStart = false,
            desiredRunning = isDesiredRunning(context),
            keepRunning = isKeepRunningEnabled(prefs),
            scheduleEnabled = ScheduleStore.isEnabled(prefs)
        )
        return when (decision) {
            ServiceRecoveryPolicy.Decision.SCHEDULE -> {
                when (val action = ScheduleEvaluator.ruleInForce(
                    ScheduleStore.load(prefs), now.toLocalDateTime()
                )?.action ?: ScheduleAction.TurnOff) {
                    is ScheduleAction.PlayPreset -> HeimdallStartupManager.buildServiceIntentForPreset(context, prefs, action.presetName)
                    ScheduleAction.TurnOff -> null
                }
            }
            ServiceRecoveryPolicy.Decision.LAST_CONFIGURATION ->
                buildLastConfigurationIntent(context, prefs) ?: HeimdallStartupManager.buildStartupServiceIntent(context, prefs)
            else -> null
        }
    }

    fun applyCurrentGlobalSettings(intent: Intent, prefs: SharedPreferences) {
        intent.putExtra(LEDService.EXTRA_ALLOW_BACKGROUND_RUN,
            isKeepRunningEnabled(prefs))
        intent.putExtra(LEDService.EXTRA_ADAPTIVE_BRIGHTNESS, prefs.getBoolean("adaptive_brightness_enabled", false))
        intent.putExtra(LEDService.EXTRA_BATTERY_SAVER_BRIGHTNESS,
            prefs.getBoolean(LEDService.PREF_BATTERY_SAVER_BRIGHTNESS, false))
        intent.putExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, prefs.getBoolean("persistent_notification_enabled", true))
        intent.putExtra(LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED, prefs.getBoolean("battery_override_when_plugged", false))
        val threshold = prefs.getInt("low_battery_alert_threshold", 0)
        intent.putExtra(LEDService.EXTRA_LOW_BATTERY_ALERT_ENABLED,
            if (prefs.contains("low_battery_alert_enabled")) prefs.getBoolean("low_battery_alert_enabled", false)
            else threshold > 0)
        intent.putExtra(LEDService.EXTRA_LOW_BATTERY_ALERT_THRESHOLD, threshold.takeIf { it in 1..100 } ?: 20)
        intent.putExtra(LEDService.EXTRA_DISABLE_LOW_BATTERY_ALERT_WHILE_CHARGING,
            prefs.getBoolean("disable_low_battery_alert_while_charging", false))
    }

    private fun configurationFrom(intent: Intent): Map<String, Any?> {
        val extras = intent.extras ?: return emptyMap()
        return ServiceRecoveryCodec.allowedKeys.filter { extras.containsKey(it) }.associateWith { extras.get(it) }
    }
}

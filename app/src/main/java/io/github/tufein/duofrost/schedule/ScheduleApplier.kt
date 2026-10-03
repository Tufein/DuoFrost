package io.github.tufein.duofrost.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.services.HeimdallStartupManager
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import java.time.ZonedDateTime

object ScheduleApplier {

    private const val TAG = "ScheduleApplier"
    private const val PREFS_NAME = "bifrost_prefs"

    fun apply(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!ScheduleStore.isEnabled(prefs)) {
            ScheduleAlarms.cancel(context)
            return
        }

        val rules = ScheduleStore.load(prefs)
        // Rearm before acting: a rejected background foreground-service start
        // must not disable every later schedule transition.
        try {
            ScheduleAlarms.scheduleNext(context, rules, now)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to schedule next rule boundary", e)
        }
        when (val action = ScheduleEvaluator.ruleInForce(rules, now.toLocalDateTime())?.action ?: ScheduleAction.TurnOff) {
            is ScheduleAction.PlayPreset -> {
                val intent = HeimdallStartupManager.buildServiceIntentForPreset(
                    context,
                    prefs,
                    action.presetName
                )
                if (intent == null) {
                    Log.w(TAG, "schedule references unknown preset '${action.presetName}'")
                } else {
                    try {
                        ContextCompat.startForegroundService(context, intent)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Scheduled background service start was rejected", e)
                    }
                }
            }

            ScheduleAction.TurnOff -> {
                ServiceRecoveryStore.markStopped(context)
                if (LEDService.isRunning) {
                    context.stopService(Intent(context, LEDService::class.java))
                }
            }
        }

    }
}

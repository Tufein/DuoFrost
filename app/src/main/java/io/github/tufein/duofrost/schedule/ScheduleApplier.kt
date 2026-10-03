package io.github.tufein.duofrost.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.services.HeimdallStartupManager
import io.github.tufein.duofrost.services.LEDService
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
                    ContextCompat.startForegroundService(context, intent)
                }
            }

            ScheduleAction.TurnOff -> {
                if (LEDService.isRunning) {
                    context.stopService(Intent(context, LEDService::class.java))
                }
            }
        }

        ScheduleAlarms.scheduleNext(context, rules, now)
    }
}

package io.github.tufein.duofrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.schedule.ScheduleApplier
import io.github.tufein.duofrost.schedule.ScheduleAlarms
import io.github.tufein.duofrost.schedule.ScheduleStore
import io.github.tufein.duofrost.services.HeimdallStartupManager
import io.github.tufein.duofrost.services.ServiceRecoveryPolicy
import io.github.tufein.duofrost.services.ServiceRecoveryStore

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isStartupSignal = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isStartupSignal) return

        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

        val signal = if (action == Intent.ACTION_BOOT_COMPLETED) ServiceRecoveryPolicy.Signal.BOOT
            else ServiceRecoveryPolicy.Signal.PACKAGE_UPDATE
        val decision = ServiceRecoveryPolicy.decide(
            signal,
            autoStart = HeimdallStartupManager.isAutoStartEnabled(prefs),
            desiredRunning = ServiceRecoveryStore.isDesiredRunning(context),
            keepRunning = ServiceRecoveryStore.isKeepRunningEnabled(prefs),
            scheduleEnabled = ScheduleStore.isEnabled(prefs)
        )
        val serviceIntent = when (decision) {
            ServiceRecoveryPolicy.Decision.SCHEDULE -> {
                ScheduleApplier.apply(context)
                return
            }
            ServiceRecoveryPolicy.Decision.AUTO_START -> HeimdallStartupManager.buildStartupServiceIntent(context, prefs)
            ServiceRecoveryPolicy.Decision.LAST_CONFIGURATION -> ServiceRecoveryStore.restoreIntent(context, prefs)
            ServiceRecoveryPolicy.Decision.NONE -> {
                // Updates must not undo explicit Stop, but preserve future
                // user schedules even if the current service stays off.
                if (ScheduleStore.isEnabled(prefs)) {
                    try {
                        ScheduleAlarms.scheduleNext(context, ScheduleStore.load(prefs))
                    } catch (e: RuntimeException) {
                        Log.w("DuoFrostStartup", "Unable to restore schedule alarm", e)
                    }
                }
                return
            }
        } ?: return
        // No capture token is included. LEDService starts as specialUse and
        // asks the user for fresh consent if this preset needs capture.
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: RuntimeException) {
            Log.w("DuoFrostStartup", "Background service start was rejected", e)
        }
    }
}

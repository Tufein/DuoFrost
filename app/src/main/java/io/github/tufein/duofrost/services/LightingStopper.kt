package io.github.tufein.duofrost.services

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.tufein.duofrost.tools.LedController
import io.github.tufein.duofrost.widgets.DuoFrostWidget

/** A durable Stop, including hardware left lit by a terminated service process. */
object LightingStopper {
    private const val TAG = "DuoFrostStop"

    fun stop(context: Context) {
        // All UI, receiver and service callers run on Android's main thread.
        // Complete the durable Stop synchronously, before another Start or
        // timer request can be handled; never post a delayed hardware clear.
        val serviceStopped = try {
            ServiceRecoveryStore.markStopped(context)
            context.stopService(Intent(context, LEDService::class.java))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to stop lighting service", e)
            return
        }

        if (!serviceStopped && !LEDService.isRunning && !ServiceRecoveryStore.isDesiredRunning(context)) {
            // No onDestroy will send the off command after process loss. This
            // one bounded write clears all zones without starting a service,
            // restarting a capture session or scheduling any further work.
            runCatching {
                val controller = LedController()
                try {
                    controller.setLedColor(0, 0, 0, 0, true, true, true, true)
                } finally {
                    controller.shutdown()
                }
            }.onFailure { Log.w(TAG, "Unable to clear retained LED output", it) }
        }

        DuoFrostTileService.refreshFrom(context)
        DuoFrostWidget.refreshFrom(context)
        LightingStateEvents.notifyChanged(context)
    }
}

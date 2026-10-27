package io.github.tufein.duofrost.services

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/** Only lighting-owned notifications; unrelated app notifications are preserved. */
internal object LightingNotifications {
    const val RUNNING_ID = 4242
    const val CAPTURE_PERMISSION_ID = 4244
    private const val TAG = "DuoFrostNotifications"

    fun cancelStopped(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (id in intArrayOf(RUNNING_ID, CAPTURE_PERMISSION_ID)) {
            runCatching { manager.cancel(id) }
                .onFailure { Log.w(TAG, "Unable to remove stopped lighting notification", it) }
        }
    }
}

package io.github.tufein.duofrost.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import io.github.tufein.duofrost.receivers.SleepTimerReceiver
import io.github.tufein.duofrost.tools.SleepTimerDeadline

/** One user-requested stop alarm. It never starts or restarts a service. */
object SleepTimerStore {
    private const val PREFS = "duofrost_sleep_timer"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    fun read(context: Context): SleepTimerDeadline? {
        val values = prefs(context).all
        val start = values["started"] as? Long ?: return null
        val deadline = values["deadline"] as? Long ?: return null
        val boot = values["boot"] as? Int ?: return null
        return SleepTimerDeadline(start, deadline, boot)
    }

    fun remaining(context: Context): Long? = read(context)?.remaining(SystemClock.elapsedRealtime(), bootCount(context))

    fun set(context: Context, minutes: Int): Boolean {
        val timer = SleepTimerDeadline.create(minutes, SystemClock.elapsedRealtime(), bootCount(context)) ?: return false
        if (!prefs(context).edit().putLong("started", timer.started)
                .putLong("deadline", timer.deadline).putInt("boot", timer.bootCount).commit()) return false
        rearm(context)
        return true
    }

    fun cancel(context: Context) {
        prefs(context).edit().clear().commit()
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pending(context)) }
    }

    fun rearm(context: Context) {
        val timer = read(context) ?: return
        if (remaining(context) == 0L) return
        // Android can defer this inexact idle alarm. The active service also has
        // one monotonic callback, and rechecks on wake and process restoration.
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadline, pending(context)
            )
        }
    }

    fun expireIfDue(context: Context): Boolean {
        if (remaining(context) != 0L) return false
        ServiceRecoveryStore.markStopped(context)
        context.stopService(Intent(context, LEDService::class.java))
        return true
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 5100, Intent(context, SleepTimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

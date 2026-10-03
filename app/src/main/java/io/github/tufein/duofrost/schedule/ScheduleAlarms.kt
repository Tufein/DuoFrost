package io.github.tufein.duofrost.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.tufein.duofrost.receivers.ScheduleReceiver
import java.time.ZonedDateTime

object ScheduleAlarms {

    private const val REQUEST_CODE = 8421

    fun scheduleNext(
        context: Context,
        rules: List<ScheduleRule>,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val next = ScheduleEvaluator.nextBoundary(rules, now)
        if (next == null) {
            cancel(context)
            return
        }

        val triggerAt = next.toInstant().toEpochMilli()
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(pendingIntent(context))
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_SCHEDULE_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

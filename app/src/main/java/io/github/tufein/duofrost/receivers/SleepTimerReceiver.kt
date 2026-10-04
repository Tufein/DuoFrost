package io.github.tufein.duofrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tufein.duofrost.services.SleepTimerStore
import io.github.tufein.duofrost.widgets.DuoFrostWidget

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!SleepTimerStore.expireIfDue(context)) SleepTimerStore.rearm(context)
        DuoFrostWidget.refreshFrom(context)
    }
}

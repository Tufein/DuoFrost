package io.github.tufein.duofrost.services

import android.content.Context
import android.content.Intent

/** Private, event-driven refreshes for a visible lighting screen. */
object LightingStateEvents {
    const val ACTION_CHANGED = "io.github.tufein.duofrost.LIGHTING_STATE_CHANGED"

    fun notifyChanged(context: Context) {
        // A screen refresh must never interrupt a durable Stop or service work.
        runCatching { context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName)) }
    }
}

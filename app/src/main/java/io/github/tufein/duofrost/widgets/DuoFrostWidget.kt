package io.github.tufein.duofrost.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import io.github.tufein.duofrost.MainActivity
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore

class DuoFrostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_STOP) {
            ServiceRecoveryStore.markStopped(context)
            context.stopService(Intent(context, LEDService::class.java))
            refreshFrom(context)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        ids.forEach { editor.remove(it.toString()) }
        editor.apply()
    }

    companion object {
        private const val ACTION_STOP = "io.github.tufein.duofrost.widget.STOP"
        private fun prefs(context: Context) = context.getSharedPreferences("duofrost_widgets", Context.MODE_PRIVATE)
        fun saveFavorite(context: Context, id: Int, name: String) {
            prefs(context).edit().putString(id.toString(), name).commit()
            refreshFrom(context)
        }

        fun renameFavorite(context: Context, previous: String, name: String) {
            val editor = prefs(context).edit()
            prefs(context).all.forEach { (id, value) -> if (value == previous) editor.putString(id, name) }
            editor.apply()
        }

        fun refreshFrom(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, DuoFrostWidget::class.java))
                    .forEach { render(context, manager, it) }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val settings = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
            val names = WidgetPresets.names(settings.getString("presets_json", null))
            val favorite = prefs(context).getString(id.toString(), null)?.takeIf { it in names }
            val enabled = ServiceRecoveryStore.isDesiredRunning(context)
            val muted = enabled && ServiceRecoveryStore.isMuted(context)
            val views = RemoteViews(context.packageName, R.layout.widget_duofrost)
            views.setTextViewText(R.id.widgetStatus, context.getString(
                if (muted) R.string.widget_muted else if (enabled) R.string.widget_enabled else R.string.widget_stopped))
            views.setTextViewText(R.id.widgetFavorite, favorite ?: context.getString(R.string.widget_choose_preset))
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_START_FROM_TILE, true)
                data = Uri.parse("duofrost://widget/$id/start")
            }
            views.setOnClickPendingIntent(R.id.widgetStart, PendingIntent.getActivity(context, id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val stop = Intent(context, DuoFrostWidget::class.java).apply {
                action = ACTION_STOP
                data = Uri.parse("duofrost://widget/$id/stop")
            }
            views.setOnClickPendingIntent(R.id.widgetStop, PendingIntent.getBroadcast(context, id, stop,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val favoriteIntent = if (favorite != null) Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_START_WIDGET_PRESET, favorite)
            } else configurationIntent(context, id)
            favoriteIntent.data = Uri.parse("duofrost://widget/$id/favorite")
            views.setOnClickPendingIntent(R.id.widgetFavorite, PendingIntent.getActivity(context, id, favoriteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val configure = configurationIntent(context, id).apply { data = Uri.parse("duofrost://widget/$id/configure") }
            views.setOnClickPendingIntent(R.id.widgetConfigure, PendingIntent.getActivity(context, id, configure,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }

        private fun configurationIntent(context: Context, id: Int) = Intent(context, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

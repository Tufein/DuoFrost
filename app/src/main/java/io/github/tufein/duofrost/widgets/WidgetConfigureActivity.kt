package io.github.tufein.duofrost.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.tufein.duofrost.R

class WidgetConfigureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val manager = AppWidgetManager.getInstance(this)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            manager.getAppWidgetInfo(id)?.provider != ComponentName(this, DuoFrostWidget::class.java)) {
            finish()
            return
        }
        val names = WidgetPresets.names(getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
            .getString("presets_json", null))
        val options = arrayOf(getString(R.string.widget_no_favorite)) + names.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_choose_preset)
            .setItems(options) { _, index ->
                DuoFrostWidget.saveFavorite(this, id, names.getOrNull(index - 1).orEmpty())
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}

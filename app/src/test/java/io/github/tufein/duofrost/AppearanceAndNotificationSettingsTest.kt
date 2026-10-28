package io.github.tufein.duofrost

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.tufein.duofrost.services.LEDService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppearanceAndNotificationSettingsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setup() {
        LEDService.isRunning = false
        prefs.edit().clear().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true)
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putBoolean("colored_logo_enabled", false).commit()
        app.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        LEDService.isRunning = false
    }

    @Test fun newThemesApplyPersistAndExportWithoutChangingRunningLighting() {
        val activity = create()
        val spinner = activity.findViewById<Spinner>(R.id.themeSpinner)
        assertEquals(7, spinner.count)
        val expected = listOf("oled_blue", "oled_green", "oled_amber", "oled_white", "midnight_teal")
        val preset = prefs.getString("last_preset_name", null)
        LEDService.isRunning = true
        for ((offset, id) in expected.withIndex()) {
            val position = offset + 2
            spinner.setSelection(position, false)
            spinner.onItemSelectedListener!!.onItemSelected(spinner, spinner.selectedView, position, position.toLong())
            assertEquals(id, prefs.getString("selected_ui_theme", null))
            val background = (activity.findViewById<View>(R.id.homeContainer).background as ColorDrawable).color
            val text = activity.findViewById<TextView>(R.id.activePresetNameText).currentTextColor
            assertTrue("Readable text for $id", ColorUtils.calculateContrast(text, background) >= 4.5)
            val popup = spinner.adapter.getDropDownView(position, null, spinner) as TextView
            assertEquals(text, popup.currentTextColor)
            val file = File(app.cacheDir, "$id.json")
            val uri = Uri.fromFile(file)
            ThemeArchiveTransfer.exportToUri(activity, uri, id, false)
            val imported = ThemeArchiveTransfer.importFromUri(activity, uri)
            assertEquals(id, imported.themeId)
            assertFalse(imported.coloredLogoEnabled)
            assertTrue(imported.errors.isEmpty())
            assertEquals(preset, prefs.getString("last_preset_name", null))
            assertNull(shadowOf(app).nextStartedService)
        }
        LEDService.isRunning = false
        controller!!.pause().stop().destroy()
        val restored = create()
        assertEquals("Midnight Teal", restored.findViewById<Spinner>(R.id.themeSpinner).selectedItem)
    }

    @Test fun visibilitySettingsTargetOnlyTheLightingCategoryAndPreserveUserBlocking() {
        val activity = create()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(LEDService.CHANNEL_ID,
            "LED Service", NotificationManager.IMPORTANCE_NONE))
        activity.findViewById<View>(R.id.gui_deviceSettingsButton).performClick()
        val button = activity.findViewById<View>(R.id.lightingNotificationSettingsButton)
        assertEquals(View.VISIBLE, button.visibility)
        LEDService.isRunning = true
        button.performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(LEDService.CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(LEDService.CHANNEL_ID).importance)
        assertTrue(LEDService.isRunning)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun visibilitySettingsCreateAMissingChannelWithoutStartingLighting() {
        val activity = create()
        val manager = app.getSystemService(NotificationManager::class.java)
        assertNull(manager.getNotificationChannel(LEDService.CHANNEL_ID))
        activity.findViewById<View>(R.id.lightingNotificationSettingsButton).performClick()
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(LEDService.CHANNEL_ID).importance)
        assertFalse(LEDService.isRunning)
        assertNull(shadowOf(app).nextStartedService)
    }

    private fun create(): MainActivity {
        controller = Robolectric.buildActivity(MainActivity::class.java).apply {
            get().setTheme(R.style.Theme_DuoFrost)
            create().start().resume().visible()
        }
        return controller!!.get()
    }
}

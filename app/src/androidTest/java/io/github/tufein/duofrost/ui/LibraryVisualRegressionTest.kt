package io.github.tufein.duofrost.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.MainActivity
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Checks actual themed controls and controller focus in the disposable debug app. */
@RunWith(AndroidJUnit4::class)
class LibraryVisualRegressionTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launchStoppedLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug"))
        assertFalse(LEDService.isRunning)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .edit().clear().commit())
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        val preset = JSONObject().put("id", "visual-default").put("name", "Default")
            .put("animationType", "STATIC").put("performanceProfile", "HIGH")
            .put("color", Color.CYAN).put("rightColor", Color.MAGENTA).put("brightness", 128)
            .put("speed", 0.5).put("smoothness", 0.5).put("isAppProfileDefault", true)
        assertTrue(prefs.edit().clear()
            .putBoolean("startup_guide_done", true)
            .putBoolean("first_launch_alert_shown", true)
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putBoolean("app_profile_info_shown", true)
            .putBoolean("auto_switch_enabled", false)
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putString("presets_json", JSONArray().put(preset).toString())
            .putString("last_preset_name", "Default")
            .putString("selected_ui_theme", "classic").commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun closeStoppedLibrary() {
        if (::scenario.isInitialized) scenario.close()
        assertFalse("Visual browsing must leave lighting stopped", LEDService.isRunning)
    }

    @Test fun checkedFiltersAndPrimaryActionsStayReadableInBothThemes() {
        val originalPresets = prefs.getString("presets_json", null)
        for (theme in listOf("classic", "oled_purple")) {
            assertTrue(prefs.edit().putString("selected_ui_theme", theme).commit())
            scenario.recreate()
            scenario.onActivity { activity ->
                val underlay = activity.findViewById<MaterialCardView>(R.id.gui_libraryCard)
                    .cardBackgroundColor.defaultColor
                val filters = activity.findViewById<LinearLayout>(R.id.gui_libraryFilters)
                val selected = filters.findViewWithTag<Chip>("library-all")
                assertTrue(selected.isChecked)
                assertReadable(selected.currentTextColor,
                    selected.chipBackgroundColor!!.getColorForState(selected.drawableState, Color.TRANSPARENT),
                    underlay, "$theme checked filter")
                for (id in listOf(R.id.gui_editLightingButton, R.id.gui_newPresetButton)) {
                    val button = activity.findViewById<MaterialButton>(id)
                    assertReadable(button.currentTextColor,
                        button.backgroundTintList!!.getColorForState(button.drawableState, Color.TRANSPARENT),
                        underlay, "$theme primary action")
                }
            }
        }
        assertEquals("Theme changes must preserve saved lighting", originalPresets, prefs.getString("presets_json", null))
    }

    @Test fun choosingAFilterRetainsControllerFocusAfterTheRowIsRebuilt() {
        scenario.onActivity { activity ->
            val row = activity.findViewById<LinearLayout>(R.id.gui_libraryFilters)
            val filter = row.findViewWithTag<Chip>("library-favorites")
            filter.isFocusableInTouchMode = true
            assertTrue(filter.requestFocus())
            assertTrue(filter.performClick())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val row = activity.findViewById<LinearLayout>(R.id.gui_libraryFilters)
            val replacement = row.findViewWithTag<Chip>("library-favorites")
            assertTrue(replacement.isChecked)
            assertTrue("The selected filter must keep controller focus", replacement.hasFocus())
            assertEquals("library-favorites", row.findFocus()?.tag)
        }
        assertEquals("Filtering must not apply lighting", "Default", prefs.getString("last_preset_name", null))
    }

    private fun assertReadable(foreground: Int, background: Int, underlay: Int, label: String) {
        val opaqueBackground = ColorUtils.compositeColors(background, underlay)
        assertTrue("$label needs readable text", ColorUtils.calculateContrast(foreground, opaqueBackground) >= 4.5)
    }
}

package io.github.tufein.duofrost.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.MainActivity
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.tools.LedOutputLimits
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Editor regressions on a disposable debug emulator; never starts the LED service. */
@RunWith(AndroidJUnit4::class)
class GuiRedesignTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launchIsolatedDebugLibrary() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug")) {
            "GUI fixtures are restricted to the debug application."
        }
        assertFalse("These tests must begin with lighting stopped", LEDService.isRunning)
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS
        )
        context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear()
            .putBoolean("startup_guide_done", true)
            .putBoolean("first_launch_alert_shown", true)
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putBoolean("app_profile_info_shown", true)
            .putBoolean("auto_switch_enabled", false)
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putInt(LedOutputLimits.PREF_MAXIMUM, 100)
            .putString("presets_json", sixPresets().toString())
            .putString("last_preset_name", "Default")
            .commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Default")))
    }

    @After fun closeEditorOnly() {
        if (::scenario.isInitialized) scenario.close()
        assertFalse("GUI actions must not start lighting", LEDService.isRunning)
    }

    @Test fun searchingKeepsSelectionUntilExplicitlyApplyingTheOriginalPresetIndex() {
        val originalPresets = prefs.getString("presets_json", null)
        search("Cozy")
        assertEquals("Default", prefs.getString("last_preset_name", null))
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Default")))
        scenario.onActivity { activity ->
            val container = activity.findViewById<android.widget.LinearLayout>(R.id.presetCoverFlowContainer)
            assertEquals(1, container.childCount)
            assertEquals("A filtered card must retain its storage index", 4, container.getChildAt(0).tag)
        }

        cozyCard().perform(scrollTo(), click())
        assertEquals("Cozy night", prefs.getString("last_preset_name", null))
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Cozy night")))
        assertEquals("Selection must not rewrite saved lighting", originalPresets, prefs.getString("presets_json", null))

        search("no matching preset")
        onView(withId(R.id.gui_emptySearch)).check(matches(withEffectiveVisibility(VISIBLE)))
        assertEquals("Cozy night", prefs.getString("last_preset_name", null))
    }

    @Test fun deviceAndAppearanceDestinationsExcludeLightingControls() {
        openLighting()
        onView(withId(R.id.modeCard)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.colorCard)).check(matches(withEffectiveVisibility(VISIBLE)))

        onView(withId(R.id.tabBehaviorSettings)).perform(scrollTo(), click())
        onView(withId(R.id.settingsSystemStatusCard)).check(matches(withEffectiveVisibility(VISIBLE)))
        assertLightingCardsGone()
        onView(withId(R.id.themesCard)).check(matches(withEffectiveVisibility(GONE)))

        onView(withId(R.id.tabThemesSettings)).perform(scrollTo(), click())
        onView(withId(R.id.themesCard)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.settingsSystemStatusCard)).check(matches(withEffectiveVisibility(GONE)))
        onView(withId(R.id.appProfileCard)).check(matches(withEffectiveVisibility(GONE)))
        assertLightingCardsGone()
    }

    @Test fun unfinishedBrightnessAndEffectSurviveRecreationThenSaveTogether() {
        openLighting()
        onView(withId(R.id.animationSpinner)).perform(scrollTo(), click())
        onData(equalTo("Breath")).perform(click())
        onView(withId(R.id.brightnessSeekBar)).perform(scrollTo(), tapSeekBar(0.36f))
        var editedBrightness = -1
        scenario.onActivity { activity ->
            editedBrightness = activity.findViewById<SeekBar>(R.id.brightnessSeekBar).progress
            assertEquals("Breath", activity.findViewById<Spinner>(R.id.animationSpinner).selectedItem)
        }
        assertNotEquals("The gesture must really alter the draft", 128, editedBrightness)
        assertEquals("STATIC", savedPreset("Default").getString("animationType"))
        assertEquals("The saved preset stays unchanged before Save", 128, savedPreset("Default").getInt("brightness"))

        scenario.recreate()
        waitForEditor(true)
        onView(withId(R.id.settingsOverlay)).check(matches(withEffectiveVisibility(VISIBLE)))
        scenario.onActivity { activity ->
            assertEquals(editedBrightness, activity.findViewById<SeekBar>(R.id.brightnessSeekBar).progress)
            assertEquals("Breath", activity.findViewById<Spinner>(R.id.animationSpinner).selectedItem)
        }
        assertEquals(128, savedPreset("Default").getInt("brightness"))

        onView(withId(R.id.closeSettingsButton)).perform(click())
        onView(withText(R.string.settings_close_confirm_title)).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).check(matches(withText(R.string.action_save))).perform(click())
        waitForEditor(false)
        assertEquals(editedBrightness, savedPreset("Default").getInt("brightness"))
        assertEquals("BREATH", savedPreset("Default").getString("animationType"))
    }

    @Test fun dashboardAndDeviceOutputLimitShareTheSamePreferenceInBothDirections() {
        onView(withId(R.id.gui_outputSeekBar)).perform(scrollTo(), tapSeekBar(0.62f))
        var homeLimit = -1
        scenario.onActivity { activity ->
            homeLimit = activity.findViewById<SeekBar>(R.id.gui_outputSeekBar).progress
            assertEquals(homeLimit, activity.findViewById<SeekBar>(R.id.maximumOutputSeekBar).progress)
        }
        assertEquals(homeLimit, prefs.getInt(LedOutputLimits.PREF_MAXIMUM, -1))

        openLighting()
        onView(withId(R.id.tabBehaviorSettings)).perform(scrollTo(), click())
        onView(withId(R.id.maximumOutputSeekBar)).perform(scrollTo(), tapSeekBar(0.34f))
        var deviceLimit = -1
        scenario.onActivity { activity ->
            deviceLimit = activity.findViewById<SeekBar>(R.id.maximumOutputSeekBar).progress
            assertEquals(deviceLimit, activity.findViewById<SeekBar>(R.id.gui_outputSeekBar).progress)
            assertEquals(activity.getString(R.string.led_output_percent, deviceLimit),
                activity.findViewById<TextView>(R.id.gui_outputValue).text.toString())
        }
        assertNotEquals(homeLimit, deviceLimit)
        assertEquals(deviceLimit, prefs.getInt(LedOutputLimits.PREF_MAXIMUM, -1))
        onView(withId(R.id.closeSettingsButton)).perform(click())
        waitForEditor(false)
        onView(withId(R.id.gui_outputValue)).check(matches(withText(context.getString(R.string.led_output_percent, deviceLimit))))
        assertEquals("A global ceiling must not rewrite preset brightness", 128, savedPreset("Default").getInt("brightness"))
    }

    @Test fun turningAppProfilesOffRestoresExplicitManualPresetSelection() {
        scenario.close()
        assertTrue(prefs.edit().putBoolean("auto_switch_enabled", true).commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        search("Cozy")
        cozyCard().perform(scrollTo(), click())
        assertEquals("App profiles own application while enabled", "Default", prefs.getString("last_preset_name", null))

        onView(withId(R.id.homeAppProfileSwitch)).perform(scrollTo(), click())
        assertFalse(prefs.getBoolean("auto_switch_enabled", true))
        cozyCard().perform(scrollTo(), click())
        assertEquals("Cozy night", prefs.getString("last_preset_name", null))
    }

    private fun search(query: String) {
        onView(withId(R.id.gui_presetSearch)).perform(scrollTo(), replaceText(query), closeSoftKeyboard())
    }

    private fun cozyCard() = onView(allOf(
        withTagValue(equalTo<Any>(4)),
        isDescendantOfA(withId(R.id.presetCoverFlowContainer))
    ))

    private fun openLighting() {
        onView(withId(R.id.gui_editLightingButton)).perform(scrollTo(), click())
        waitForEditor(true)
    }

    private fun assertLightingCardsGone() {
        listOf(R.id.modeCard, R.id.colorCard, R.id.animationCard).forEach {
            onView(withId(it)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    private fun savedPreset(name: String): JSONObject {
        val array = JSONArray(prefs.getString("presets_json", "[]"))
        return (0 until array.length()).map { array.getJSONObject(it) }
            .single { it.getString("name") == name }
    }

    private fun sixPresets(): JSONArray {
        val names = arrayOf("Default", "Ocean", "Sunset", "Music", "Cozy night", "Arcade")
        val effects = arrayOf("STATIC", "AMBIENT", "FADE_TRANSITION", "AUDIO_REACTIVE", "BREATH", "RAINBOW")
        return JSONArray().apply {
            names.indices.forEach { index ->
                put(JSONObject().put("name", names[index]).put("animationType", effects[index])
                    .put("performanceProfile", "HIGH").put("color", -16_711_681)
                    .put("rightColor", -65_536).put("brightness", 128)
                    .put("speed", 0.5).put("smoothness", 0.5)
                    .put("isAppProfileDefault", index == 0))
            }
        }
    }

    /** Real touch input makes the production listener receive fromUser=true. */
    private fun tapSeekBar(fraction: Float): ViewAction = GeneralClickAction(
        Tap.SINGLE,
        CoordinatesProvider { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val width = view.width - view.paddingLeft - view.paddingRight
            floatArrayOf(location[0] + view.paddingLeft + width * fraction,
                location[1] + view.height / 2f)
        },
        Press.FINGER
    )

    /** Wait for the actual visible transition, rather than sleeping a fixed duration. */
    private fun waitForEditor(open: Boolean) {
        onView(withId(R.id.gui_root)).perform(object : ViewAction {
            override fun getDescription() = "Wait for editor to settle ${if (open) "open" else "closed"}"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun perform(uiController: UiController, root: View) {
                val deadline = SystemClock.uptimeMillis() + 3_000L
                do {
                    uiController.loopMainThreadUntilIdle()
                    val overlay = root.findViewById<View>(R.id.settingsOverlay)
                    val settled = if (open) overlay.visibility == View.VISIBLE && overlay.alpha >= 0.99f && kotlin.math.abs(overlay.translationX) < 1f
                        else overlay.visibility == View.GONE
                    if (settled) return
                    uiController.loopMainThreadForAtLeast(50L)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Editor did not settle ${if (open) "open" else "closed"}")
            }
        })
    }
}

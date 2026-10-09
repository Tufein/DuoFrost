package io.github.tufein.duofrost.scenes

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import org.hamcrest.Matchers.equalTo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Debug-emulator fixtures: configuration actions must never start lighting. */
@RunWith(AndroidJUnit4::class)
class ScenesActivityTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var scenario: ActivityScenario<ScenesActivity>

    @Before fun prepareEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug"))
        assertFalse("Begin with lighting stopped", LEDService.isRunning)
        context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear()
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putBoolean("auto_switch_enabled", false)
            .putString("presets_json", JSONArray().put(JSONObject().put("id", "test-ocean")
                .put("name", "Ocean").put("animationType", "STATIC")
                .put("performanceProfile", "HIGH").put("color", -16_711_681)
                .put("brightness", 128).put("speed", 0.5).put("smoothness", 0.5)).toString())
            .commit())
        scenario = ActivityScenario.launch(ScenesActivity::class.java)
    }

    @After fun closeEditor() {
        if (::scenario.isInitialized) scenario.close()
        assertFalse("Scene editing must not start lighting", LEDService.isRunning)
        assertFalse("Smart Scenes must not enable old app switching", prefs.getBoolean("auto_switch_enabled", true))
    }

    @Test fun scenesBeginDisabledAndCanBeEnabledWithoutStartingLighting() {
        onView(withId(R.id.scenes_enable)).check(matches(isNotChecked()))
        onView(withId(R.id.scenes_empty)).perform(scrollTo()).check(matches(isDisplayed()))
        assertFalse(SceneStore(prefs).isEnabled)
        onView(withId(R.id.scenes_enable)).perform(scrollTo(), click())
        assertTrue(SceneStore(prefs).isEnabled)
        assertFalse(LEDService.isRunning)
    }

    @Test fun savingPresetRuleKeepsStableIdentityAndDoesNotStartLighting() {
        val presetId = PresetRepository(prefs).list().single().id
        onView(withId(R.id.scenes_add_rule)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_rule_name)).perform(replaceText("Evening ocean"), closeSoftKeyboard())
        onView(withId(R.id.scenes_rule_effect)).perform(scrollTo(), click())
        onData(equalTo("Ocean")).inRoot(isPlatformPopup()).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        val rule = SceneStore(prefs).loadRules().single()
        assertEquals("Evening ocean", rule.name)
        assertEquals(presetId, rule.presetId)
        assertEquals(SceneTarget.ANY, rule.target)
        assertFalse("Saving a scene must leave automation off", SceneStore(prefs).isEnabled)
        assertFalse(LEDService.isRunning)
        scenario.recreate()
        onView(withText("Evening ocean")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test fun dimmingOnlyRuleSavesBatteryConditionWithoutReplacingPreset() {
        onView(withId(R.id.scenes_add_rule)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_rule_name)).perform(replaceText("Low battery"), closeSoftKeyboard())
        onView(withId(R.id.scenes_rule_battery)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_rule_battery_percent)).perform(scrollTo(), replaceText("25"), closeSoftKeyboard())
        onView(withId(R.id.scenes_rule_brightness)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_rule_brightness_percent)).perform(scrollTo(), replaceText("30"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())
        val rule = SceneStore(prefs).loadRules().single()
        assertEquals(null, rule.presetId)
        assertEquals(25, rule.maxBatteryPercent)
        assertEquals(30, rule.maxBrightnessPercent)
        assertFalse(LEDService.isRunning)
    }

    @Test fun editingAppGroupPreservesPackagesWithoutStartingLighting() {
        assertTrue(SceneStore(prefs).saveGroups(listOf(AppGroup("test-group", "Emulators", setOf("com.example.emulator")))))
        onView(withId(R.id.scenes_groups)).perform(scrollTo())
            .check(matches(isCompletelyDisplayed())).perform(click())
        onView(withText(R.string.scenes_edit)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_group_name)).perform(replaceText("Retro games"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())
        val group = SceneStore(prefs).loadGroups().single()
        assertEquals("Retro games", group.name)
        assertEquals(setOf("com.example.emulator"), group.packages)
        assertFalse(LEDService.isRunning)
    }
}

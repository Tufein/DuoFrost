package io.github.tufein.duofrost.scenes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.LightingStateEvents
import io.github.tufein.duofrost.services.LightingStopper
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.services.SleepTimerStore
import org.hamcrest.Matcher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end service state tests on the disposable debug emulator, without hardware claims. */
@RunWith(AndroidJUnit4::class)
class ScenesRuntimeTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var store: SceneStore
    private lateinit var scenario: ActivityScenario<ScenesActivity>
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun prepareStoppedDebugRuntime() {
        context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug")) {
            "Runtime fixtures must run in the isolated debug app."
        }
        assertFalse("Another test must not leave lighting running", LEDService.isRunning)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        instrumentation.runOnMainSync { SleepTimerStore.cancel(context) }
        assertTrue(context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .edit().clear().commit())
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear()
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, true)
            .putBoolean("auto_switch_enabled", false)
            .putBoolean("game_scene_enabled", false)
            .putBoolean("schedule_enabled", false)
            .putBoolean("battery_override_when_plugged", false)
            .putBoolean("low_battery_alert_enabled", false)
            .putInt("low_battery_alert_threshold", 0)
            .putBoolean("adaptive_brightness_enabled", false)
            .putBoolean(LEDService.PREF_BATTERY_SAVER_BRIGHTNESS, false)
            .putString("last_preset_name", BASE_NAME)
            .putString("presets_json", fixturePresets().toString())
            .commit())
        store = SceneStore(prefs)
        assertEquals(3, PresetRepository(prefs).list().size)
        scenario = ActivityScenario.launch(ScenesActivity::class.java)
    }

    @After fun stopAndClose() {
        if (::context.isInitialized) instrumentation.runOnMainSync { LightingStopper.stop(context) }
        if (::scenario.isInitialized) {
            await("lighting stopped during cleanup") { !LEDService.isRunning }
            scenario.close()
        }
        if (::context.isInitialized) assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
        assertFalse(LEDService.isRunning)
    }

    @Test fun presetAndBrightnessScenesPreserveBaselineThenAcceptANewManualStart() {
        saveRules(SceneRule("evening-rule", "Evening scene", presetId = NIGHT_ID))
        instrumentation.runOnMainSync { store.isEnabled = true }
        startVisible(BASE_NAME, BASE_COLOR, 210)
        await("automatic preset selected") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == NIGHT_NAME &&
                LEDService.activeSceneReason?.contains("Evening scene") == true
        }
        assertBaseRecoveryConfiguration(BASE_COLOR, 210)

        // Replacing a preset scene with a modifier restores the user's preset,
        // while applying the cap in the existing running service.
        saveRules(SceneRule("quiet-rule", "Quiet output", maxBrightnessPercent = 25))
        await("brightness modifier preserves the running baseline") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == BASE_NAME &&
                LEDService.activeSceneReason?.contains("Quiet output") == true
        }
        assertBaseRecoveryConfiguration(BASE_COLOR, 210)
        assertTrue(ServiceRecoveryStore.isDesiredRunning(context))

        instrumentation.runOnMainSync { store.isEnabled = false }
        await("disabling scenes restores manual lighting") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == BASE_NAME &&
                LEDService.activeSceneReason == context.getString(R.string.scene_runtime_manual)
        }

        startVisible(MANUAL_NAME, MANUAL_COLOR, 90)
        await("new manual full configuration is accepted") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == MANUAL_NAME &&
                LEDService.activeSceneReason == context.getString(R.string.scene_runtime_manual)
        }
        assertBaseRecoveryConfiguration(MANUAL_COLOR, 90)
        assertFalse(prefs.getBoolean("auto_switch_enabled", true))
    }

    @Test fun temporaryChoiceOverridesRulesButDurableStopCannotBeUndoneByEditing() {
        saveRules(SceneRule("game-night", "Game night", presetId = NIGHT_ID))
        instrumentation.runOnMainSync { store.isEnabled = true }
        startVisible(BASE_NAME, BASE_COLOR, 210)
        await("scene begins while service is running") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == NIGHT_NAME
        }
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        instrumentation.runOnMainSync {
            assertTrue(store.setTemporaryScene(MANUAL_ID, 15, SystemClock.elapsedRealtime(), bootCount))
            LightingStateEvents.notifyChanged(context)
        }
        await("temporary user choice takes priority") {
            LEDService.isRunning && prefs.getString("last_preset_name", null) == MANUAL_NAME &&
                LEDService.activeSceneReason?.contains(MANUAL_NAME) == true
        }
        instrumentation.runOnMainSync { LightingStopper.stop(context) }
        await("explicit Stop is durable") { !LEDService.isRunning && !ServiceRecoveryStore.isDesiredRunning(context) }
        assertNull(store.getTemporaryScene(SystemClock.elapsedRealtime(), bootCount))
        assertFalse(prefs.contains(SceneStore.PREF_KEY_TEMPORARY_PRESET))

        // Real editor controls, following a Stop, must remain configuration-only.
        saveRules(SceneRule("edited-night", "Edited night", presetId = NIGHT_ID, maxBrightnessPercent = 40))
        onView(withId(R.id.scenes_enable)).perform(scrollTo(), click())
        onView(withId(R.id.scenes_enable)).perform(click())
        assertTrue(store.isEnabled)
        assertStoppedFor(1_500L)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    private fun saveRules(vararg rules: SceneRule) {
        instrumentation.runOnMainSync {
            assertTrue(store.saveRules(rules.toList()))
            LightingStateEvents.notifyChanged(context)
        }
    }

    private fun startVisible(name: String, color: Int, brightness: Int) {
        scenario.onActivity { activity ->
            assertTrue(prefs.edit().putString("last_preset_name", name).commit())
            activity.startForegroundService(Intent(activity, LEDService::class.java).apply {
                putExtra("animationType", "STATIC")
                putExtra("performanceProfile", "HIGH")
                putExtra("animationColor", color)
                putExtra("animationRightColor", color)
                putExtra(LEDService.EXTRA_FADE_END_COLOR, color)
                putExtra(LEDService.EXTRA_FADE_END_RIGHT_COLOR, color)
                putExtra("brightness", brightness)
                putExtra("speed", 0.5f)
                putExtra("smoothness", 0.5f)
                putExtra("sensitivity", 0.5f)
                putExtra("saturationBoost", 0f)
                putExtra("useCustomSampling", false)
                putExtra("useSingleColor", false)
                putExtra("breatheWhenCharging", false)
                putExtra("indicateChargingSpeed", false)
                putExtra("flashWhenReady", false)
                putExtra("ambientDisplayId", 0)
                ServiceRecoveryStore.applyCurrentGlobalSettings(this, prefs)
            })
        }
    }

    private fun assertBaseRecoveryConfiguration(color: Int, brightness: Int) {
        val intent = ServiceRecoveryStore.buildLastConfigurationIntent(context, prefs)
        assertNotNull("A scene must retain the user's recoverable configuration", intent)
        assertEquals("STATIC", intent!!.getStringExtra("animationType"))
        assertEquals(color, intent.getIntExtra("animationColor", 0))
        assertEquals(brightness, intent.getIntExtra("brightness", -1))
    }

    private fun await(description: String, condition: () -> Boolean) {
        onView(withId(R.id.scenes_root)).perform(object : ViewAction {
            override fun getDescription() = "Wait up to five seconds for $description"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun perform(controller: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 5_000L
                do {
                    controller.loopMainThreadUntilIdle()
                    if (condition()) return
                    controller.loopMainThreadForAtLeast(50L)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Timed out: $description; running=${LEDService.isRunning}; " +
                    "preset=${prefs.getString("last_preset_name", null)}; reason=${LEDService.activeSceneReason}")
            }
        })
    }

    private fun assertStoppedFor(duration: Long) {
        onView(withId(R.id.scenes_root)).perform(object : ViewAction {
            override fun getDescription() = "Observe that scene edits do not undo explicit Stop"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun perform(controller: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + duration
                do {
                    controller.loopMainThreadUntilIdle()
                    assertFalse("Scene editing must not restart the stopped service", LEDService.isRunning)
                    assertFalse("Scene editing must not restore running intent", ServiceRecoveryStore.isDesiredRunning(context))
                    controller.loopMainThreadForAtLeast(50L)
                } while (SystemClock.uptimeMillis() < deadline)
            }
        })
    }

    private fun fixturePresets() = JSONArray().apply {
        put(preset(BASE_ID, BASE_NAME, BASE_COLOR, 210))
        put(preset(NIGHT_ID, NIGHT_NAME, NIGHT_COLOR, 120))
        put(preset(MANUAL_ID, MANUAL_NAME, MANUAL_COLOR, 90))
    }
    private fun preset(id: String, name: String, color: Int, brightness: Int) = JSONObject()
        .put("id", id).put("name", name).put("animationType", "STATIC")
        .put("performanceProfile", "HIGH").put("color", color).put("rightColor", color)
        .put("brightness", brightness).put("speed", 0.5).put("smoothness", 0.5)

    private companion object {
        const val BASE_ID = "runtime-base"
        const val BASE_NAME = "Baseline red"
        const val BASE_COLOR = -65_536
        const val NIGHT_ID = "runtime-night"
        const val NIGHT_NAME = "Night blue"
        const val NIGHT_COLOR = -16_776_961
        const val MANUAL_ID = "runtime-manual"
        const val MANUAL_NAME = "Manual amber"
        const val MANUAL_COLOR = -24_576
    }
}

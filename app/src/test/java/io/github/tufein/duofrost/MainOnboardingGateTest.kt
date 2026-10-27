package io.github.tufein.duofrost

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.tufein.duofrost.services.LEDService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainOnboardingGateTest {
    private lateinit var prefs: SharedPreferences
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setup() {
        LEDService.isRunning = false
        val context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putString("presets_json", """[{"name":"Default","animationType":"STATIC","performanceProfile":"HIGH","color":16711680,"rightColor":255,"brightness":128,"speed":0.5,"smoothness":0.5}]""")
            .putString("last_preset_name", "Default")
            .commit()
        context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun close() {
        controller?.pause()?.stop()?.destroy()
        LEDService.isRunning = false
    }

    @Test fun notificationPinningIsSentDuringAnActiveStartTransition() {
        prefs.edit().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true).commit()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = createMain(Intent().putExtra(MainActivity.EXTRA_START_WIDGET_PRESET, "Default"))
        val app = shadowOf(RuntimeEnvironment.getApplication())
        assertNotNull(app.nextStartedService)
        // The initial Start was dispatched; its UI transition is still active.
        LEDService.isRunning = true
        activity.findViewById<SwitchMaterial>(R.id.persistentNotificationSwitch).isChecked = false
        val update = app.nextStartedService
        assertNotNull(update)
        assertEquals(LEDService.ACTION_UPDATE_PARAMS, update.action)
        assertEquals(setOf(LEDService.EXTRA_PERSISTENT_NOTIFICATION), update.extras!!.keySet())
        assertFalse(update.getBooleanExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, true))
        assertFalse(prefs.getBoolean("persistent_notification_enabled", true))
        assertNull(app.nextStartedService)
    }

    @Test fun firstLaunchOnlyOpensTheGuideAndLeavesTheAppInactive() {
        val activity = createMain()
        val shadow = shadowOf(activity)
        val guide = shadow.nextStartedActivityForResult
        assertEquals(StartupGuideActivity::class.java.name, guide.intent.component?.className)
        assertNull(shadow.nextStartedActivityForResult)
        assertNull(shadow.lastRequestedPermission)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.gui_root).visibility)
        assertFalse(prefs.getBoolean(StartupGuideActivity.PREF_DONE, false))
        assertFalse(prefs.getBoolean("first_launch_alert_shown", false))
    }

    @Test fun launchRequestsWaitForOnboardingAndSurviveOrdinaryReopening() {
        val activity = createMain(Intent().putExtra(MainActivity.EXTRA_START_FROM_TILE, true))
        val shadow = shadowOf(activity)
        assertNotNull(shadow.nextStartedActivityForResult)

        controller!!.newIntent(Intent().putExtra(MainActivity.EXTRA_START_WIDGET_PRESET, "Default")
            .putExtra(MainActivity.EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE, true))
        controller!!.newIntent(Intent())

        assertEquals("Default", activity.intent.getStringExtra(MainActivity.EXTRA_START_WIDGET_PRESET))
        assertFalse(activity.intent.getBooleanExtra(MainActivity.EXTRA_START_FROM_TILE, false))
        assertTrue(activity.intent.getBooleanExtra(MainActivity.EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE, false))
        assertNull(shadow.nextStartedActivityForResult)
        assertNull(shadow.lastRequestedPermission)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.gui_root).visibility)
    }

    @Test fun recreationWaitsForTheExistingGuideAndPreservesTheDeferredRequest() {
        val activity = createMain()
        assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        controller!!.newIntent(Intent().putExtra(MainActivity.EXTRA_START_WIDGET_PRESET, "Default"))
        val saved = Bundle()
        controller!!.saveInstanceState(saved).pause().stop().destroy()

        val restored = createMain(state = saved)
        val shadow = shadowOf(restored)
        assertEquals("Default", restored.intent.getStringExtra(MainActivity.EXTRA_START_WIDGET_PRESET))
        assertEquals(View.INVISIBLE, restored.findViewById<View>(R.id.gui_root).visibility)
        assertNull(shadow.nextStartedActivityForResult)
        assertNull(shadow.lastRequestedPermission)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }

    @Test fun cancellingTheGuideClosesTheUninitializedMainActivity() {
        val activity = createMain()
        val shadow = shadowOf(activity)
        val guide = shadow.nextStartedActivityForResult
        shadow.receiveResult(guide.intent, Activity.RESULT_CANCELED, null)

        assertTrue(activity.isFinishing)
        assertFalse(prefs.getBoolean(StartupGuideActivity.PREF_DONE, false))
        assertNull(shadow.lastRequestedPermission)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }

    @Test fun aPrematureSuccessfulResultCannotBypassTheCompletionPreference() {
        val activity = createMain()
        val shadow = shadowOf(activity)
        val guide = shadow.nextStartedActivityForResult
        shadow.receiveResult(guide.intent, Activity.RESULT_OK, null)

        assertTrue(activity.isFinishing)
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.gui_root).visibility)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }

    @Test fun completingTheGuideProcessesTheOriginalWidgetRequestOnce() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = createMain(Intent().putExtra(MainActivity.EXTRA_START_WIDGET_PRESET, "Default"))
        val shadow = shadowOf(activity)
        val guide = shadow.nextStartedActivityForResult
        prefs.edit().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true).commit()
        shadow.receiveResult(guide.intent, Activity.RESULT_OK, null)

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.gui_root).visibility)
        assertFalse(activity.isFinishing)
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_START_WIDGET_PRESET))
        val service = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertNotNull(service)
        assertEquals("STATIC", service.getStringExtra("animationType"))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        assertNull(shadow.lastRequestedPermission)
        assertNull(shadow.nextStartedActivityForResult)
    }

    @Test fun completedInstallationsOpenNormallyAndReplayIsConfinedToDeviceSettings() {
        prefs.edit().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true).commit()
        val activity = createMain()
        val shadow = shadowOf(activity)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.gui_root).visibility)
        assertNull(shadow.nextStartedActivityForResult)
        assertNull(shadow.lastRequestedPermission)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.replayOnboardingButton).visibility)

        activity.findViewById<View>(R.id.gui_deviceSettingsButton).performClick()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.replayOnboardingButton).visibility)
        activity.findViewById<View>(R.id.replayOnboardingButton).performClick()
        val replay = shadow.nextStartedActivity
        assertEquals(StartupGuideActivity::class.java.name, replay.component?.className)
        assertTrue(replay.getBooleanExtra(StartupGuideActivity.EXTRA_REPLAY, false))
        assertTrue(prefs.getBoolean(StartupGuideActivity.PREF_DONE, false))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }

    private fun createMain(launch: Intent = Intent(), state: Bundle? = null): MainActivity {
        controller = Robolectric.buildActivity(MainActivity::class.java, launch).apply {
            get().setTheme(R.style.Theme_DuoFrost)
            create(state).start().resume().visible()
        }
        return controller!!.get()
    }
}

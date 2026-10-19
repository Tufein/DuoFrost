package io.github.tufein.duofrost.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.MainActivity
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.tools.CrashReporter
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual startup warning in the disposable debug app, with lighting stopped. */
@RunWith(AndroidJUnit4::class)
class StartupDialogLifecycleTest {
    private lateinit var application: Application
    private lateinit var prefs: SharedPreferences
    private var scenario: ActivityScenario<MainActivity>? = null
    private val welcomeWindows = mutableMapOf<MainActivity, WelcomeWindow>()

    private data class WelcomeWindow(
        val host: MainActivity,
        val decor: View,
        var detachedAtHostDestroy: Boolean? = null
    )

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is MainActivity) return
            // Register before MainActivity creates its welcome. Destroy events run in reverse
            // observer order, so this records cleanup before Android forcibly removes leaked
            // windows after onDestroy returns. A post-destroy attachment check alone misses that.
            activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    welcomeWindows[activity]?.let { window ->
                        window.detachedAtHostDestroy = !window.decor.isAttachedToWindow
                    }
                }
            })
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before fun launchUnconfirmedWelcomeWithLightingStopped() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug")) {
            "Startup dialog fixtures are restricted to the debug application."
        }
        assertFalse("These tests must begin with lighting stopped", LEDService.isRunning)
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS
        )
        assertTrue(context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .edit().clear().commit())
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear()
            .putBoolean("startup_guide_done", true)
            .putBoolean("first_launch_alert_shown", false)
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putBoolean("app_profile_info_shown", true)
            .putBoolean("auto_switch_enabled", false)
            .putBoolean("auto_start_heimdall", false)
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .commit())
        CrashReporter.clear(context)
        application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun closeWelcomeAndLeaveLightingStopped() {
        try {
            scenario?.close()
        } finally {
            if (::application.isInitialized) {
                application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            }
            welcomeWindows.clear()
        }
        assertFalse("Startup dialogs must not start lighting", LEDService.isRunning)
    }

    @Test fun recreationClosesOldWelcomeAndOnlyPositiveTapMarksItRead() {
        val original = captureWelcomeWindow()
        assertFalse(prefs.getBoolean("first_launch_alert_shown", true))

        scenario!!.recreate()

        assertDestroyedWindowIsClosed(original)
        assertFalse("Recreation must not accept the welcome",
            prefs.getBoolean("first_launch_alert_shown", true))
        val replacement = captureWelcomeWindow()
        assertNotSame(original.host, replacement.host)
        assertNotSame(original.decor, replacement.decor)

        onView(withId(android.R.id.button1)).inRoot(isDialog())
            .check(matches(withText(R.string.alert_action_ok))).perform(click())
        assertTrue("Only the positive action marks the welcome read",
            prefs.getBoolean("first_launch_alert_shown", false))
        assertFalse("Confirmation must remove the visible welcome", replacement.decor.isAttachedToWindow)

        scenario!!.recreate()
        onView(withId(R.id.dialogTitle)).check(doesNotExist())
        assertTrue(prefs.getBoolean("first_launch_alert_shown", false))
        assertFalse(LEDService.isRunning)
    }

    @Test fun closingVisibleWelcomeDoesNotConsumeItForTheNextLaunch() {
        val original = captureWelcomeWindow()

        scenario!!.close()
        scenario = null

        assertDestroyedWindowIsClosed(original)
        assertFalse("Closing the Activity must not accept the welcome",
            prefs.getBoolean("first_launch_alert_shown", true))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val replacement = captureWelcomeWindow()
        assertNotSame(original.host, replacement.host)
        assertNotSame(original.decor, replacement.decor)
        assertFalse(prefs.getBoolean("first_launch_alert_shown", true))
        assertFalse(LEDService.isRunning)
    }

    private fun captureWelcomeWindow(): WelcomeWindow {
        lateinit var host: MainActivity
        scenario!!.onActivity { host = it }
        var decor: View? = null
        onView(allOf(withId(R.id.dialogTitle), withText(R.string.beta_alert_title)))
            .inRoot(isDialog()).check(matches(isDisplayed())).check { title, error ->
                if (error != null) throw error
                decor = title.rootView
            }
        val window = WelcomeWindow(host, checkNotNull(decor))
        assertNotSame("Capture the dialog window, not the Activity window", host.window.decorView, window.decor)
        assertTrue("The actual welcome window must start attached", window.decor.isAttachedToWindow)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { welcomeWindows[host] = window }
        return window
    }

    private fun assertDestroyedWindowIsClosed(window: WelcomeWindow) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("The original Activity must really be destroyed", window.host.isDestroyed)
        assertEquals("Close the welcome during host destruction, before framework leak cleanup",
            true, window.detachedAtHostDestroy)
        assertFalse("The previous welcome must no longer have a window", window.decor.isAttachedToWindow)
    }
}

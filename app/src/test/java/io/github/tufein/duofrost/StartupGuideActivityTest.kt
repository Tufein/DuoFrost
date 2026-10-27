package io.github.tufein.duofrost

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowServiceManager
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StartupGuideActivityTest {
    private var controller: ActivityController<StartupGuideActivity>? = null
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

    @Before fun setup() {
        prefs.edit().clear().commit()
        app.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
        LEDService.isRunning = false
        hardware(false)
    }

    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        LEDService.isRunning = false
        ShadowServiceManager.reset()
    }

    @Test fun permissionsAreExplainedBeforeAnyRequestAndOptionalAccessDoesNotBlockTheTour() {
        val activity = create()
        assertStep(1)
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(activity.getString(R.string.onboarding_not_allowed),
            activity.findViewById<TextView>(R.id.guideNotificationStatus).text.toString())
        click(R.id.guideNext)
        assertStep(2)
        assertFalse(StartupGuideActivity.isComplete(app))
        click(R.id.guideNext)
        assertStep(3)
        assertFalse(activity.findViewById<View>(R.id.guideNext).isEnabled)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun allSixColorsNeedConfirmationBeforeTheFinalActionCompletesSetup() {
        hardware(true)
        val activity = create()
        toTest()
        assertNull(activity.findViewById<View>(R.id.guideTestNextColor))
        click(R.id.guideStartTest)
        for (index in 1..6) {
            assertStep(3)
            assertTrue(activity.findViewById<TextView>(R.id.guideTestExpected).text.contains("$index of 6"))
            assertFalse(StartupGuideActivity.isComplete(app))
            click(R.id.guideTestNextColor)
        }
        assertStep(4)
        assertTrue(allText(activity.window.decorView).contains(activity.getString(R.string.onboarding_test_passed)))
        assertFalse(StartupGuideActivity.isComplete(app))
        click(R.id.guideNext)
        assertTrue(StartupGuideActivity.isComplete(app))
        assertTrue(prefs.getBoolean("first_launch_alert_shown", false))
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(shadowOf(app).nextStartedService)
        assertFalse(app.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .getBoolean("desired_running", false))
    }

    @Test fun unavailableHardwareOffersHelpAndNeverClaimsTheLedsPassed() {
        val activity = create()
        toTest()
        assertNull(activity.findViewById<View>(R.id.guideTestUnresolved))
        click(R.id.guideStartTest)
        assertStep(3)
        assertTrue(allText(activity.window.decorView).contains(activity.getString(R.string.onboarding_test_unavailable)))
        assertNull(activity.findViewById<View>(R.id.guideTestNextColor))
        click(R.id.guideTestUnresolved)
        assertStep(4)
        assertTrue(allText(activity.window.decorView).contains(activity.getString(R.string.onboarding_test_not_confirmed)))
        assertFalse(allText(activity.window.decorView).contains(activity.getString(R.string.onboarding_test_passed)))
        assertFalse(StartupGuideActivity.isComplete(app))
        click(R.id.guideNext)
        assertTrue(StartupGuideActivity.isComplete(app))
    }

    @Test fun closingTheFirstPageLeavesSetupIncomplete() {
        val activity = create()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
        assertFalse(StartupGuideActivity.isComplete(app))
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun recreationPreservesThePageAndReturningFromSettingsRefreshesAccess() {
        var activity = create()
        click(R.id.guideNext)
        val state = Bundle()
        controller!!.saveInstanceState(state).pause().stop().destroy()
        activity = create(state)
        assertStep(2)
        click(R.id.guideBack)
        controller!!.pause()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller!!.resume()
        assertStep(1)
        assertEquals(activity.getString(R.string.onboarding_allowed),
            activity.findViewById<TextView>(R.id.guideNotificationStatus).text.toString())
        assertNull(activity.findViewById<View>(R.id.buttonGrantNotifications))
        assertFalse(StartupGuideActivity.isComplete(app))
    }

    @Test fun pauseAndTimeoutRequireAnExplicitTestRestart() {
        hardware(true)
        val activity = create()
        toTest()
        click(R.id.guideStartTest)
        click(R.id.guideTestNextColor)
        controller!!.pause().resume()
        assertStep(3)
        assertNull(activity.findViewById<View>(R.id.guideTestNextColor))
        assertNotNull(activity.findViewById<View>(R.id.guideStartTest))
        click(R.id.guideStartTest)
        assertTrue(activity.findViewById<TextView>(R.id.guideTestExpected).text.contains("1 of 6"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertNull(activity.findViewById<View>(R.id.guideTestNextColor))
        assertNotNull(activity.findViewById<View>(R.id.guideStartTest))
        assertFalse(StartupGuideActivity.isComplete(app))
    }

    @Test fun aSavedDonePageWithoutATestOutcomeCannotCompleteSetup() {
        create(Bundle().apply { putInt("page", 3) })
        assertStep(3)
        click(R.id.guideNext)
        assertFalse(StartupGuideActivity.isComplete(app))
    }

    @Test
    @Config(qualifiers = "w640dp-h280dp-land-mdpi")
    fun shortLandscapeKeepsEveryPageScrollableWithUniqueAccessibleControls() {
        hardware(true)
        val activity = create()
        fun checkPage() {
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            root.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 640, 280)
            val scroll = activity.findViewById<ScrollView>(R.id.guideScroll)
            assertTrue(scroll.height >= 96)
            assertTrue(scroll.getChildAt(0).height > scroll.height)
            val ids = mutableSetOf<Int>()
            fun visit(view: View) {
                if (view.id != View.NO_ID) assertTrue("Duplicate control ID", ids.add(view.id))
                if (view is MaterialButton && view.visibility == View.VISIBLE) {
                    assertTrue("Touch target height", view.height >= 48)
                    assertTrue("Touch target width", view.width >= 48)
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(root)
        }
        checkPage()
        click(R.id.guideNext)
        checkPage()
        click(R.id.guideNext)
        checkPage()
        click(R.id.guideStartTest)
        checkPage()
        repeat(6) { click(R.id.guideTestNextColor) }
        checkPage()
    }

    private fun create(state: Bundle? = null): StartupGuideActivity {
        controller = Robolectric.buildActivity(StartupGuideActivity::class.java).apply {
            get().setTheme(R.style.Theme_DuoFrost)
            create(state).start().resume().visible()
        }
        return controller!!.get()
    }

    private fun click(id: Int) { controller!!.get().findViewById<View>(id).performClick() }
    private fun toTest() { click(R.id.guideNext); click(R.id.guideNext) }
    private fun assertStep(step: Int) = assertTrue(controller!!.get()
        .findViewById<TextView>(R.id.guideProgressText).text.startsWith("Step $step of 4"))
    private fun allText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { allText(view.getChildAt(it)) }
        else -> ""
    }

    private fun hardware(available: Boolean) {
        ShadowServiceManager.addBinderService("PServerBinder", IInterface::class.java,
            object : IInterface { override fun asBinder(): IBinder = Binder() })
        ShadowServiceManager.setServiceAvailability("PServerBinder", available)
    }
}

package io.github.tufein.duofrost

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.services.DuoFrostAccessibilityService
import io.github.tufein.duofrost.tools.LedDiagnosticFrame
import io.github.tufein.duofrost.tools.OnboardingLedTestSession
import io.github.tufein.duofrost.ui.SecondaryScreenUi

/** Permissions stay optional; only the final action completes the first-run guide. */
class StartupGuideActivity : io.github.tufein.duofrost.ui.ThemedActivity() {
    companion object {
        const val PREF_DONE = "startup_guide_done"
        const val EXTRA_REPLAY = "replay_onboarding"
        fun isComplete(context: Context): Boolean =
            context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).getBoolean(PREF_DONE, false)
    }

    private enum class Page(val title: Int) {
        PERMISSIONS(R.string.onboarding_permissions), EXPLANATION(R.string.onboarding_explanation),
        TEST(R.string.onboarding_test), DONE(R.string.onboarding_ready)
    }
    private enum class TestOutcome { UNTESTED, PASSED, UNRESOLVED }

    private var page = Page.PERMISSIONS
    private var testOutcome = TestOutcome.UNTESTED
    private var testIndex = 0
    private var testAttempted = false
    private var testProblem = false
    private var testFailure: OnboardingLedTestSession.Result? = null
    private lateinit var content: LinearLayout
    private lateinit var next: MaterialButton
    private lateinit var back: MaterialButton
    private lateinit var testSession: OnboardingLedTestSession
    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }
    private val profiles by lazy { AppProfileManager(prefs) }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (page == Page.PERMISSIONS) render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page.entries.getOrNull(savedInstanceState?.getInt("page") ?: 0) ?: Page.PERMISSIONS
        testOutcome = TestOutcome.entries.getOrNull(savedInstanceState?.getInt("testOutcome") ?: 0) ?: TestOutcome.UNTESTED
        if (page == Page.DONE && testOutcome == TestOutcome.UNTESTED) page = Page.TEST
        testIndex = (savedInstanceState?.getInt("testIndex") ?: 0).coerceIn(LedDiagnosticFrame.steps.indices)
        testAttempted = savedInstanceState?.getBoolean("testAttempted") ?: false
        testProblem = savedInstanceState?.getBoolean("testProblem") ?: false
        testFailure = OnboardingLedTestSession.Result.entries.firstOrNull {
            it.name == savedInstanceState?.getString("testFailure")
        }
        setContentView(R.layout.activity_startup_guide)
        SecondaryScreenUi.applyInsets(findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        content = findViewById(R.id.guidePageContainer)
        next = findViewById(R.id.guideNext)
        back = findViewById(R.id.guideBack)
        testSession = OnboardingLedTestSession(this, onTimeout = {
            if (page == Page.TEST) render()
        })
        next.setOnClickListener {
            when (page) {
                Page.PERMISSIONS -> navigate(Page.EXPLANATION)
                Page.EXPLANATION -> navigate(Page.TEST)
                Page.TEST -> Unit
                Page.DONE -> complete()
            }
        }
        back.setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::testSession.isInitialized) render()
    }

    override fun onPause() {
        // Rotation, Home and permission screens stop output; returning needs an explicit restart.
        testSession.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page.ordinal)
        outState.putInt("testOutcome", testOutcome.ordinal)
        outState.putInt("testIndex", testIndex)
        outState.putBoolean("testAttempted", testAttempted)
        outState.putBoolean("testProblem", testProblem)
        outState.putString("testFailure", testFailure?.name)
        super.onSaveInstanceState(outState)
    }

    private fun navigate(target: Page) {
        testSession.stop()
        page = target
        render()
        findViewById<ScrollView>(R.id.guideScroll).scrollTo(0, 0)
    }

    private fun goBack() {
        if (page == Page.PERMISSIONS) {
            setResult(RESULT_CANCELED)
            finish()
        } else navigate(Page.entries[page.ordinal - 1])
    }

    private fun render() {
        content.removeAllViews()
        findViewById<TextView>(R.id.guideProgressText).text = getString(
            R.string.onboarding_progress, page.ordinal + 1, getString(page.title))
        back.setText(if (page == Page.PERMISSIONS) R.string.onboarding_close else R.string.onboarding_back)
        next.isEnabled = page != Page.TEST
        next.setText(when (page) {
            Page.PERMISSIONS -> R.string.onboarding_to_explanation
            Page.EXPLANATION -> R.string.onboarding_to_test
            Page.TEST -> R.string.onboarding_test_required
            Page.DONE -> R.string.onboarding_open_app
        })
        when (page) {
            Page.PERMISSIONS -> showPermissions()
            Page.EXPLANATION -> showExplanation()
            Page.TEST -> showTest()
            Page.DONE -> showDone()
        }
    }

    private fun showPermissions() {
        body(content, R.string.onboarding_permissions_body)
        val notifications = hasNotifications()
        permissionCard(R.string.onboarding_notifications_title, R.string.onboarding_notifications_body,
            R.id.guideNotificationStatus, notifications, R.id.buttonGrantNotifications,
            if (prefs.getBoolean("onboarding_notifications_requested", false) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
                R.string.onboarding_app_settings else R.string.onboarding_notifications_action) {
            if (prefs.getBoolean("onboarding_notifications_requested", false) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else {
                prefs.edit().putBoolean("onboarding_notifications_requested", true).apply()
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionCard(R.string.gui_setup_accessibility_title, R.string.onboarding_accessibility_body,
            R.id.guideAccessibilityStatus, DuoFrostAccessibilityService.isEnabled(this),
            R.id.buttonOpenAccessibility, R.string.onboarding_accessibility_action) {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        permissionCard(R.string.onboarding_usage_title, R.string.onboarding_usage_body,
            R.id.guideUsageStatus, profiles.hasUsageStatsPermission(this), R.id.buttonOpenUsageAccess,
            R.string.onboarding_usage_action) {
            openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun permissionCard(title: Int, description: Int, statusId: Int, granted: Boolean,
        actionId: Int, label: Int, onClick: () -> Unit) {
        val card = card(title, description)
        body(card, if (granted) R.string.onboarding_allowed else R.string.onboarding_not_allowed, statusId)
        if (!granted) action(card, actionId, label, onClick)
    }

    private fun showExplanation() {
        card(R.string.onboarding_choose_title, R.string.onboarding_choose_body)
        card(R.string.onboarding_start_title, R.string.onboarding_start_body)
        card(R.string.onboarding_capture_title, R.string.onboarding_capture_body)
        card(R.string.onboarding_background_title, R.string.onboarding_background_body)
    }

    private fun showTest() {
        val test = card(R.string.onboarding_test, R.string.onboarding_test_body)
        test.addView(SecondaryScreenUi.body(this, getString(R.string.onboarding_test_expected,
            testIndex + 1, LedDiagnosticFrame.steps.size, LedDiagnosticFrame.steps[testIndex].label), false).apply {
            id = R.id.guideTestExpected
            setPadding(0, dp(16), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val frame = LedDiagnosticFrame.steps[testIndex]
        addSwatch(swatches, R.string.onboarding_left_stick, frame.left)
        addSwatch(swatches, R.string.onboarding_right_stick, frame.right)
        test.addView(swatches)
        body(test, if (testSession.isActive) R.string.onboarding_test_sent
            else if (testAttempted) R.string.onboarding_test_paused else R.string.onboarding_start_test,
            R.id.guideTestStatus)
        if (!testSession.isActive) {
            action(test, R.id.guideStartTest, if (testAttempted) R.string.onboarding_restart_test
                else R.string.onboarding_start_test) { startTest() }
        } else {
            action(test, R.id.guideTestNextColor, if (testIndex == LedDiagnosticFrame.steps.lastIndex)
                R.string.onboarding_test_confirm else R.string.onboarding_test_match) {
                // A timeout can occur after this view was rendered.
                if (!testSession.isActive) { render(); return@action }
                if (testIndex == LedDiagnosticFrame.steps.lastIndex) {
                    testOutcome = TestOutcome.PASSED
                    navigate(Page.DONE)
                } else {
                    testIndex++
                    acceptTestResult(testSession.showStep(testIndex))
                    render()
                }
            }
        }
        if (testAttempted) action(test, R.id.guideTestProblem, R.string.onboarding_test_problem) {
            testProblem = true
            testSession.stop()
            render()
        }
        if (testProblem) {
            val help = card(R.string.onboarding_test_help_title, R.string.onboarding_test_help_body)
            when (testFailure) {
                OnboardingLedTestSession.Result.HARDWARE_UNAVAILABLE -> body(help, R.string.onboarding_test_unavailable)
                OnboardingLedTestSession.Result.COMMAND_REJECTED -> body(help, R.string.onboarding_test_rejected)
                else -> Unit
            }
            action(help, R.id.guideTestUnresolved, R.string.onboarding_test_unresolved) {
                testOutcome = TestOutcome.UNRESOLVED
                navigate(Page.DONE)
            }
        }
    }

    private fun startTest() {
        testIndex = 0
        testAttempted = true
        testProblem = false
        testOutcome = TestOutcome.UNTESTED
        acceptTestResult(testSession.start())
        render()
    }

    private fun acceptTestResult(result: OnboardingLedTestSession.Result) {
        testFailure = result
        if (result != OnboardingLedTestSession.Result.STARTED) testProblem = true
    }

    private fun showDone() {
        val done = card(if (testOutcome == TestOutcome.PASSED) R.string.gui_setup_complete_title
            else R.string.onboarding_unresolved_title, R.string.onboarding_done_body)
        body(done, if (testOutcome == TestOutcome.PASSED) R.string.onboarding_test_passed
            else R.string.onboarding_test_not_confirmed)
        if (!hasNotifications() || !DuoFrostAccessibilityService.isEnabled(this) ||
            !profiles.hasUsageStatsPermission(this)) body(done, R.string.onboarding_permissions_pending)
        body(done, R.string.beta_alert_body)
    }

    private fun complete() {
        if (page != Page.DONE || testOutcome == TestOutcome.UNTESTED) return
        testSession.stop()
        prefs.edit().putBoolean(PREF_DONE, true).putBoolean("first_launch_alert_shown", true).apply()
        setResult(RESULT_OK)
        finish()
    }

    private fun hasNotifications(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(intent: Intent) {
        if (runCatching { startActivity(intent) }.isFailure)
            Toast.makeText(this, R.string.onboarding_settings_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun card(title: Int, description: Int): LinearLayout {
        val (view, children) = SecondaryScreenUi.card(this)
        children.addView(SecondaryScreenUi.heading(this, getString(title)).apply { textSize = 20f })
        body(children, description)
        content.addView(view)
        return children
    }

    private fun body(parent: LinearLayout, text: Int, viewId: Int = View.NO_ID) {
        parent.addView(SecondaryScreenUi.body(this, getString(text)).apply {
            id = viewId
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun action(parent: LinearLayout, viewId: Int, label: Int, onClick: () -> Unit) {
        parent.addView(SecondaryScreenUi.action(this, getString(label), onClick = onClick).apply {
            id = viewId
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        })
    }

    private fun addSwatch(parent: LinearLayout, label: Int, rgb: Int) {
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(View(this@StartupGuideActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xff000000.toInt() or rgb)
                    setStroke(dp(2), SecondaryScreenUi.color(this@StartupGuideActivity, R.color.bifrost_outline))
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            body(this, label)
        })
    }

    private fun dp(value: Int) = SecondaryScreenUi.dp(this, value)
}

package io.github.tufein.duofrost.tools

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.LightingStateEvents
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class OnboardingLedTestSessionTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var recovery: SharedPreferences
    private lateinit var output: FakeOutput
    private lateinit var session: OnboardingLedTestSession
    private var running = false
    private var timedOut = 0
    private var acceptsCommand = true
    private val serviceCommands = mutableListOf<Int>()

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        recovery = context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        recovery.edit().clear().commit()
        shadowOf(context.getSystemService(PowerManager::class.java)).apply {
            setIsInteractive(true)
            setIsPowerSaveMode(false)
        }
        output = FakeOutput()
        session = OnboardingLedTestSession(context, { output }, { running }, {
            serviceCommands += it
            acceptsCommand
        }, Handler(Looper.getMainLooper()), { timedOut++ })
    }

    @Test fun testingWithLightingOffNeverStartsServiceOrChangesStoredChoices() {
        prefs.edit().putBoolean("auto_start", true).putString("last_preset_name", "Ocean").commit()
        recovery.edit().putBoolean("desired_running", true)
            .putString("configuration", "retained recovery configuration").commit()
        val settingsBefore = prefs.all
        val recoveryBefore = recovery.all

        assertEquals(OnboardingLedTestSession.Result.STARTED, session.start(4))
        assertArrayEquals(intArrayOf(0x404040, 0x404040, 0, 0), output.colors())
        session.showStep(5)
        assertArrayEquals(intArrayOf(0, 0, 0x404040, 0x404040), output.colors())
        session.stop()

        assertTrue(serviceCommands.isEmpty())
        assertEquals(settingsBefore, prefs.all)
        assertEquals(recoveryBefore, recovery.all)
        assertEquals(FakeOutput.Frame(null, true), output.frames.last())
        assertEquals(1, output.shutdowns)
        assertFalse(session.isActive)
    }

    @Test fun directOutputHonorsGlobalBatteryScreenAndMuteCeilingsAndRedrawsOnEvents() {
        prefs.edit().putInt(LedOutputLimits.PREF_MAXIMUM, 10)
            .putBoolean(LEDService.PREF_BATTERY_SAVER_BRIGHTNESS, true)
            .putInt(LedOutputLimits.PREF_BATTERY_SAVER, 5)
            .putBoolean(LedOutputLimits.PREF_SCREEN_OFF_ENABLED, true)
            .putInt(LedOutputLimits.PREF_SCREEN_OFF, 2).commit()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        session.start(3)
        assertArrayEquals(IntArray(4) { 0x0d0d0d }, output.colors())

        broadcast(Intent.ACTION_SCREEN_OFF)
        assertArrayEquals(IntArray(4) { 0x050505 }, output.colors())
        ServiceRecoveryStore.setMuted(context, true)
        broadcast(LightingStateEvents.ACTION_CHANGED)
        assertArrayEquals(IntArray(4), output.colors())

        ServiceRecoveryStore.setMuted(context, false)
        broadcast(Intent.ACTION_SCREEN_ON)
        assertArrayEquals(IntArray(4) { 0x0d0d0d }, output.colors())
        prefs.edit().putBoolean(LEDService.PREF_BATTERY_SAVER_BRIGHTNESS, false).commit()
        idle()
        assertArrayEquals(IntArray(4) { 0x1a1a1a }, output.colors())
        prefs.edit().putInt(LedOutputLimits.PREF_MAXIMUM, 100).commit()
        idle()
        assertArrayEquals(IntArray(4) { 0x404040 }, output.colors())
        assertTrue(serviceCommands.isEmpty())
        session.stop()
    }

    @Test fun initialScreenAndMuteSnapshotsCanSuppressTheVeryFirstFrame() {
        prefs.edit().putBoolean(LedOutputLimits.PREF_SCREEN_OFF_ENABLED, true).commit()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        session.start()
        assertArrayEquals(IntArray(4), output.colors())
        session.stop()

        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        ServiceRecoveryStore.setMuted(context, true)
        session.start()
        assertArrayEquals(IntArray(4), output.colors())
        session.stop()
    }

    @Test fun timeoutRestoresOnceAndOnlyExplicitStepsRenewTheDeadline() {
        session.start()
        advance(59)
        prefs.edit().putInt(LedOutputLimits.PREF_MAXIMUM, 50).commit()
        idle()
        advance(1)
        assertFalse(session.isActive)
        assertEquals(1, timedOut)
        assertEquals(FakeOutput.Frame(null, true), output.frames.last())
        assertEquals(OnboardingLedTestSession.Result.STOPPED, session.showStep(1))
        advance(60)
        assertEquals(1, timedOut)

        session.start()
        advance(59)
        session.showStep(1)
        advance(59)
        assertTrue(session.isActive)
        advance(1)
        assertFalse(session.isActive)
        assertEquals(2, timedOut)
    }

    @Test fun existingLightingReceivesOnlyOverlayStepsAndClear() {
        running = true
        recovery.edit().putBoolean("desired_running", true).putString("configuration", "original").commit()
        val before = recovery.all
        assertEquals(OnboardingLedTestSession.Result.STARTED, session.start(2))
        session.showStep(4)
        session.stop()
        assertEquals(listOf(2, 4, -1), serviceCommands)
        assertTrue(output.frames.isEmpty())
        assertTrue(output.limits.isEmpty())
        assertEquals(before, recovery.all)
        advance(60)
        assertEquals(0, timedOut)
    }

    @Test fun serviceTakingOwnershipGetsTheOverlayWithoutBlackFromTheDirectController() {
        session.start(2)
        running = true
        broadcast(LightingStateEvents.ACTION_CHANGED)
        assertEquals(FakeOutput.Frame(null, false), output.frames.last())
        assertEquals(listOf(2), serviceCommands)
        val frameCount = output.frames.size
        session.showStep(5)
        session.stop()
        assertEquals(listOf(2, 5, -1), serviceCommands)
        assertEquals(frameCount, output.frames.size)
    }

    @Test fun pauseAfterAServiceStartsCannotBlackItsOutputEvenBeforeItsBroadcast() {
        session.start()
        running = true
        session.stop()
        assertEquals(FakeOutput.Frame(null, false), output.frames.last())
        assertTrue(serviceCommands.isEmpty())
    }

    @Test fun stoppingExistingLightingPausesTheTestInsteadOfRestartingOutput() {
        running = true
        session.start(3)
        running = false
        broadcast(LightingStateEvents.ACTION_CHANGED)
        assertFalse(session.isActive)
        assertEquals(1, timedOut)
        assertEquals(listOf(3), serviceCommands)
        assertTrue(output.frames.isEmpty())
        advance(60)
        assertEquals(1, timedOut)
    }

    @Test fun unsupportedHardwareAndRejectedServiceCommandsCannotClaimStarted() {
        output.available = false
        assertEquals(OnboardingLedTestSession.Result.HARDWARE_UNAVAILABLE, session.start())
        assertFalse(session.isActive)
        assertTrue(output.frames.isEmpty())
        assertTrue(serviceCommands.isEmpty())
        advance(60)
        assertEquals(0, timedOut)

        output.available = true
        running = true
        acceptsCommand = false
        assertEquals(OnboardingLedTestSession.Result.COMMAND_REJECTED, session.start())
        assertFalse(session.isActive)
        assertTrue(output.frames.isEmpty())
    }

    @Test fun explicitStopDropsTimeoutAndEventCallbacksUntilAnotherUserStart() {
        session.start()
        session.stop()
        val frames = output.frames.toList()
        val limits = output.limits.toList()
        prefs.edit().putInt(LedOutputLimits.PREF_MAXIMUM, 1).commit()
        broadcast(Intent.ACTION_SCREEN_OFF)
        running = true
        broadcast(LightingStateEvents.ACTION_CHANGED)
        advance(120)
        assertEquals(frames, output.frames)
        assertEquals(limits, output.limits)
        assertTrue(serviceCommands.isEmpty())
        assertEquals(0, timedOut)
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private class FakeOutput : OnboardingLedTestSession.Output {
        data class Frame(val value: LedDiagnosticFrame?, val redraw: Boolean)
        var available = true
        override val isAvailable: Boolean get() = available
        val frames = mutableListOf<Frame>()
        val limits = mutableListOf<Int>()
        var shutdowns = 0
        private var frame: LedDiagnosticFrame? = null
        private var limit = 255
        override fun setOutputBrightnessLimit(limit: Int) {
            this.limit = limit
            limits += limit
        }
        override fun setDiagnosticFrame(frame: LedDiagnosticFrame?, redraw: Boolean) {
            this.frame = frame
            frames += Frame(frame, redraw)
        }
        override fun shutdown() { shutdowns++ }
        fun colors(): IntArray = IntArray(4) { frame?.colorAt(it, limit) ?: 0 }
    }
}

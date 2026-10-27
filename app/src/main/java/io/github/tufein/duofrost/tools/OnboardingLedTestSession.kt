package io.github.tufein.duofrost.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.LightingStateEvents
import io.github.tufein.duofrost.services.ServiceRecoveryStore

/** A visible, bounded diagnostic overlay that never starts a lighting session. */
class OnboardingLedTestSession internal constructor(
    context: Context,
    private val outputFactory: () -> Output,
    private val serviceRunning: () -> Boolean,
    private val serviceStep: (Int) -> Boolean,
    private val handler: Handler,
    private val onTimeout: () -> Unit
) {
    constructor(context: Context, onTimeout: () -> Unit = {}) : this(
        context,
        { ControllerOutput() },
        { LEDService.isRunning },
        { index ->
            kotlin.runCatching {
                context.applicationContext.startService(Intent(context, LEDService::class.java).apply {
                    action = LEDService.ACTION_LED_TEST
                    putExtra(LEDService.EXTRA_TEST_STEP, index)
                }) != null
            }.getOrDefault(false)
        },
        Handler(Looper.getMainLooper()),
        onTimeout
    )

    enum class Result { STARTED, HARDWARE_UNAVAILABLE, COMMAND_REJECTED, STOPPED }

    internal interface Output {
        val isAvailable: Boolean
        fun setOutputBrightnessLimit(limit: Int)
        fun setDiagnosticFrame(frame: LedDiagnosticFrame?, redraw: Boolean = true)
        fun shutdown()
    }

    private class ControllerOutput : Output {
        private val controller = LedController()
        override val isAvailable: Boolean get() = controller.isAvailable
        override fun setOutputBrightnessLimit(limit: Int) = controller.setOutputBrightnessLimit(limit)
        override fun setDiagnosticFrame(frame: LedDiagnosticFrame?, redraw: Boolean) =
            controller.setDiagnosticFrame(frame, redraw)
        override fun shutdown() = controller.shutdown()
    }

    private enum class Mode { NONE, DIRECT, SERVICE }

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
    private val powerManager = this.context.getSystemService(PowerManager::class.java)
    private var interactive = powerManager?.isInteractive ?: true
    private var mode = Mode.NONE
    private var output: Output? = null
    private var currentStep = 0
    private var observing = false
    var isActive: Boolean = false
        private set

    private val timeout = Runnable { pause() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshOutput()
    }
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> interactive = false
                Intent.ACTION_SCREEN_ON -> interactive = true
            }
            refreshOutput()
        }
    }

    /** STARTED means the frame was requested; the person testing confirms its appearance. */
    fun start(index: Int = 0): Result {
        if (index !in LedDiagnosticFrame.steps.indices) return Result.COMMAND_REJECTED
        if (isActive) return showStep(index)
        val candidate = runCatching(outputFactory).getOrNull() ?: return Result.COMMAND_REJECTED
        if (!candidate.isAvailable) {
            candidate.shutdown()
            return Result.HARDWARE_UNAVAILABLE
        }
        output = candidate
        interactive = powerManager?.isInteractive ?: true
        isActive = true
        if (!observe()) {
            stop()
            return Result.COMMAND_REJECTED
        }
        return showStep(index)
    }

    /** Each explicit step has one 60-second expiry; state broadcasts do not renew it. */
    fun showStep(index: Int): Result {
        if (index !in LedDiagnosticFrame.steps.indices) return Result.COMMAND_REJECTED
        if (!isActive) return Result.STOPPED
        currentStep = index
        if (!displayStep()) {
            stop()
            return Result.COMMAND_REJECTED
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return Result.STARTED
    }

    /** The owning activity calls this onPause; no output is resumed automatically. */
    fun stop() {
        handler.removeCallbacks(timeout)
        if (observing) {
            context.unregisterReceiver(stateReceiver)
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            observing = false
        }
        isActive = false
        when (mode) {
            Mode.SERVICE -> if (serviceRunning()) serviceStep(-1)
            Mode.DIRECT -> releaseDirect()
            Mode.NONE -> Unit
        }
        mode = Mode.NONE
        output?.shutdown()
        output = null
    }

    private fun observe(): Boolean = runCatching {
        ContextCompat.registerReceiver(context, stateReceiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(LightingStateEvents.ACTION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        observing = true
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }.isSuccess

    private fun displayStep(): Boolean {
        if (serviceRunning()) {
            if (mode == Mode.DIRECT) releaseDirect()
            mode = Mode.SERVICE
            return serviceStep(currentStep)
        }
        // Stopping existing lighting is explicit intent, so the test pauses too.
        if (mode == Mode.SERVICE) return false
        mode = Mode.DIRECT
        val controller = output ?: return false
        controller.setOutputBrightnessLimit(outputLimit())
        controller.setDiagnosticFrame(LedDiagnosticFrame.steps[currentStep])
        return true
    }

    private fun refreshOutput() {
        if (!isActive) return
        if (mode == Mode.SERVICE && !serviceRunning()) {
            pause()
        } else if (mode == Mode.DIRECT && serviceRunning()) {
            if (!displayStep()) pause()
        } else if (mode == Mode.DIRECT) {
            output?.setOutputBrightnessLimit(outputLimit())
        }
    }

    private fun outputLimit(): Int {
        val values = prefs.all
        return LedOutputLimits.fromStoredValues(values).resolve(
            batterySaverEnabled = values[LEDService.PREF_BATTERY_SAVER_BRIGHTNESS] as? Boolean ?: false,
            batterySaverActive = powerManager?.isPowerSaveMode ?: false,
            interactive = interactive,
            muted = ServiceRecoveryStore.isMuted(context)
        )
    }

    private fun releaseDirect() {
        // A newly started service owns the hardware now. Discard this overlay
        // without emitting the direct controller's empty (black) baseline.
        output?.setDiagnosticFrame(null, redraw = !serviceRunning())
    }

    private fun pause() {
        if (!isActive) return
        stop()
        onTimeout()
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}

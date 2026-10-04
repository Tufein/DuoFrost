package io.github.tufein.duofrost.services

import android.content.Intent
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ServiceController(
    private val activity: AppCompatActivity,
    private val handler: Handler,
    private val debounceDelay: Long,
    private val restartDelay: Long
) {

    var isServiceTransitioning: Boolean = false
        private set

    private var isOperationInProgress = false
    private var lastOperationTime = 0L
    private var pendingServiceOperation: Runnable? = null
    private var operationToken = 0

    var onNeedsMediaProjectionCheck: (() -> Unit)? = null

    fun cancelPendingOperations() {
        pendingServiceOperation?.let { handler.removeCallbacks(it) }
        pendingServiceOperation = null
        isOperationInProgress = false
        isServiceTransitioning = false
        operationToken++
    }

    private fun beginOperationWindow() {
        val now = System.currentTimeMillis()
        if (now - lastOperationTime < debounceDelay) {
            cancelPendingOperations()
        }
        lastOperationTime = now
        isOperationInProgress = true
        isServiceTransitioning = true
        operationToken++
    }

    private fun finishOperationWindowWithGraceDelay() {
        isOperationInProgress = false
        handler.postDelayed({
            isServiceTransitioning = false
        }, 200)
    }

    fun startDebounced(createIntent: () -> Intent) {
        if (isOperationInProgress) return
        beginOperationWindow()
        // Dispatch an authorized Start before onPause can cancel UI work.
        // Closing the app immediately afterwards must leave a real foreground
        // service for the continuation/recovery policy to manage.
        try {
            ContextCompat.startForegroundService(activity.applicationContext, createIntent())
        } finally {
            finishOperationWindowWithGraceDelay()
        }
    }

    fun stopDebounced() {
        cancelPendingOperations()
        beginOperationWindow()
        // A requested Stop must survive onPause cancelling pending UI work.
        try {
            LightingStopper.stop(activity)
        } finally {
            finishOperationWindowWithGraceDelay()
        }
    }

    fun restartDebounced(needsMediaProjectionCheck: Boolean = false, createIntent: () -> Intent) {
        if (isOperationInProgress) return

        if (needsMediaProjectionCheck) {
            onNeedsMediaProjectionCheck?.invoke()
            return
        }
        beginOperationWindow()
        val token = operationToken

        pendingServiceOperation = Runnable {
            if (token != operationToken) return@Runnable
            try {
                // LEDService already applies a full configuration in place.
                // Avoid a stopped-service gap that onPause could cancel before
                // the second half of a stop/start restart was delivered.
                ContextCompat.startForegroundService(activity.applicationContext, createIntent())
                finishOperationWindowWithGraceDelay()
            } catch (e: Exception) {
                isOperationInProgress = false
                isServiceTransitioning = false
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }
}

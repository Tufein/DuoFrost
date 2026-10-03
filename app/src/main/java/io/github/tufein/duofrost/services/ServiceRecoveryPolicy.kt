package io.github.tufein.duofrost.services

object ServiceRecoveryPolicy {
    enum class Signal { BOOT, PACKAGE_UPDATE, STICKY_RESTART }
    enum class Decision { NONE, SCHEDULE, AUTO_START, LAST_CONFIGURATION }

    fun decide(
        signal: Signal,
        autoStart: Boolean,
        desiredRunning: Boolean,
        keepRunning: Boolean,
        scheduleEnabled: Boolean
    ): Decision {
        if (signal != Signal.BOOT && (!desiredRunning || !keepRunning)) {
            return Decision.NONE
        }
        if (scheduleEnabled) return Decision.SCHEDULE
        return when (signal) {
            Signal.BOOT -> if (autoStart) Decision.AUTO_START else Decision.NONE
            Signal.PACKAGE_UPDATE, Signal.STICKY_RESTART -> {
                if (desiredRunning && keepRunning) Decision.LAST_CONFIGURATION else Decision.NONE
            }
        }
    }
}

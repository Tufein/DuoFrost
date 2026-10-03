package io.github.tufein.duofrost.services

object ServiceRecoveryPolicy {
    enum class Signal { BOOT, PACKAGE_UPDATE, STICKY_RESTART, APP_OPEN }
    enum class Decision { NONE, SCHEDULE, AUTO_START, LAST_CONFIGURATION }

    fun decide(
        signal: Signal,
        autoStart: Boolean,
        desiredRunning: Boolean,
        keepRunning: Boolean,
        scheduleEnabled: Boolean
    ): Decision {
        val resumeWanted = desiredRunning && keepRunning
        val permitted = when (signal) {
            Signal.BOOT -> autoStart || scheduleEnabled
            Signal.APP_OPEN -> autoStart || resumeWanted
            Signal.PACKAGE_UPDATE, Signal.STICKY_RESTART -> resumeWanted
        }
        if (!permitted) {
            return Decision.NONE
        }
        if (scheduleEnabled) return Decision.SCHEDULE
        return when (signal) {
            Signal.BOOT -> if (autoStart) Decision.AUTO_START else Decision.NONE
            Signal.APP_OPEN -> if (resumeWanted) Decision.LAST_CONFIGURATION else Decision.AUTO_START
            Signal.PACKAGE_UPDATE, Signal.STICKY_RESTART -> {
                if (desiredRunning && keepRunning) Decision.LAST_CONFIGURATION else Decision.NONE
            }
        }
    }
}

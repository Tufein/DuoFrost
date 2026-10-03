package io.github.tufein.duofrost.services

import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.tufein.duofrost.services.ServiceRecoveryPolicy.Decision
import io.github.tufein.duofrost.services.ServiceRecoveryPolicy.Signal

class ServiceRecoveryPolicyTest {
    @Test fun systemReclaimRestoresWantedLightingWithoutBootAutoStart() {
        assertEquals(Decision.LAST_CONFIGURATION, ServiceRecoveryPolicy.decide(
            Signal.STICKY_RESTART, false, true, true, false))
    }

    @Test fun explicitStopBlocksStickyRestoreIncludingScheduledEffects() {
        for (scheduled in listOf(false, true)) {
            assertEquals(Decision.NONE, ServiceRecoveryPolicy.decide(
                Signal.STICKY_RESTART, true, false, true, scheduled))
        }
    }

    @Test fun disablingBackgroundModeBlocksStickyRestore() {
        assertEquals(Decision.NONE, ServiceRecoveryPolicy.decide(
            Signal.STICKY_RESTART, true, true, false, true))
    }

    @Test fun activeScheduleWinsOverStaleConfigurationDuringRecovery() {
        assertEquals(Decision.SCHEDULE, ServiceRecoveryPolicy.decide(
            Signal.STICKY_RESTART, true, true, true, true))
    }

    @Test fun bootDoesNotResumeLastSessionWithoutAutoStart() {
        assertEquals(Decision.NONE, ServiceRecoveryPolicy.decide(
            Signal.BOOT, false, true, true, false))
    }

    @Test fun bootAutoStartIsIndependentOfBackgroundSettingAndPreviousStop() {
        assertEquals(Decision.AUTO_START, ServiceRecoveryPolicy.decide(
            Signal.BOOT, true, false, false, false))
    }

    @Test fun bootScheduleWinsOverAutoStartIncludingAnOffRule() {
        assertEquals(Decision.SCHEDULE, ServiceRecoveryPolicy.decide(
            Signal.BOOT, true, false, false, true))
    }

    @Test fun installingUpdateDoesNotUndoExplicitStopOrBackgroundOptOut() {
        assertEquals(Decision.NONE, ServiceRecoveryPolicy.decide(
            Signal.PACKAGE_UPDATE, true, false, true, true))
        assertEquals(Decision.NONE, ServiceRecoveryPolicy.decide(
            Signal.PACKAGE_UPDATE, true, true, false, true))
    }

    @Test fun installingUpdateResumesWantedSessionAndHonorsItsSchedule() {
        assertEquals(Decision.LAST_CONFIGURATION, ServiceRecoveryPolicy.decide(
            Signal.PACKAGE_UPDATE, false, true, true, false))
        assertEquals(Decision.SCHEDULE, ServiceRecoveryPolicy.decide(
            Signal.PACKAGE_UPDATE, true, true, true, true))
    }
}

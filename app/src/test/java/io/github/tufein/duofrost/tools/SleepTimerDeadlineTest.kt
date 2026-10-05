package io.github.tufein.duofrost.tools

import org.junit.Assert.*
import org.junit.Test

class SleepTimerDeadlineTest {
    @Test fun supportedDurationsUseElapsedTime() {
        for (minutes in listOf(1, 15, 30, 60, 120)) {
            val timer = SleepTimerDeadline.create(minutes, 10_000, 4)!!
            assertEquals(minutes * 60_000L, timer.remaining(10_000, 4))
            assertEquals(minutes * 60_000L - 1000, timer.remaining(11_000, 4))
        }
    }
    @Test fun restoredSnapshotRetainsOriginalDeadline() {
        val timer = SleepTimerDeadline.create(30, 1000, 7)!!
        val restored = SleepTimerDeadline(timer.started, timer.deadline, timer.bootCount)
        assertEquals(1_200_000L, restored.remaining(601_000, 7))
    }
    @Test fun customDurationSurvivesRestorationAndExpiresAtItsOriginalDeadline() {
        val timer = SleepTimerDeadline.create(37, 10_000, 7)!!
        val restored = SleepTimerDeadline(timer.started, timer.deadline, timer.bootCount)
        assertEquals(1_620_000L, restored.remaining(610_000, 7))
        assertEquals(1L, restored.remaining(2_229_999, 7))
        assertEquals(0L, restored.remaining(2_230_000, 7))
    }
    @Test fun exactDeadlineAndLongSleepAreExpired() {
        val timer = SleepTimerDeadline.create(15, 1000, 2)!!
        assertEquals(1, timer.remaining(timer.deadline - 1, 2))
        assertEquals(0, timer.remaining(timer.deadline, 2))
        assertEquals(0, timer.remaining(timer.deadline + 3_600_000, 2))
    }
    @Test fun rebootExpiresRatherThanExtendingPreviousSession() {
        val timer = SleepTimerDeadline.create(60, 100, 5)!!
        assertEquals(0, timer.remaining(200, 6))
    }
    @Test fun elapsedClockResetExpiresEvenWithoutBootCount() {
        val timer = SleepTimerDeadline.create(30, 60_000, -1)!!
        assertEquals(0, timer.remaining(100, -1))
        assertEquals(1_799_900, timer.remaining(60_100, -1))
    }
    @Test fun invalidDurationsAndOverflowAreRejected() {
        for (minutes in listOf(Int.MIN_VALUE, -1, 0, 121, Int.MAX_VALUE))
            assertNull(SleepTimerDeadline.create(minutes, 100, 1))
        assertNull(SleepTimerDeadline.create(1, -1, 1))
        assertNull(SleepTimerDeadline.create(1, Long.MAX_VALUE, 1))
    }
    @Test fun corruptedSnapshotsExpireSafely() {
        for (timer in listOf(SleepTimerDeadline(-1, 100, 1), SleepTimerDeadline(100, 100, 1),
            SleepTimerDeadline(100, 99, 1), SleepTimerDeadline(0, Long.MAX_VALUE, 1))) {
            assertEquals(0, timer.remaining(100, 1))
        }
    }
}

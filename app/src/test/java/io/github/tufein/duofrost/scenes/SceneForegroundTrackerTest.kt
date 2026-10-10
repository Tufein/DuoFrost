package io.github.tufein.duofrost.scenes

import org.junit.Assert.*
import org.junit.Test

class SceneForegroundTrackerTest {
    private val tracker = SceneForegroundTracker()

    private fun observe(packageName: String?, at: Long, enabled: Boolean = true) =
        tracker.resolve(packageName, at, enabled)

    @Test fun firstKnownForegroundAppliesImmediately() {
        assertNull(observe(null, 0))
        assertEquals("game", observe("game", 10))
    }

    @Test fun changedForegroundMustRemainObservedForHalfASecond() {
        observe("game", 0)
        assertEquals("game", observe("home", 700))
        assertEquals("game", observe("home", 1_199))
        assertEquals("home", observe("home", 1_200))
        assertEquals("home", observe("home", 1_201))
    }

    @Test fun briefAppHopDoesNotRestartTheOriginalScene() {
        observe("game", 0)
        assertEquals("game", observe("permission-dialog", 700))
        assertEquals("game", observe("game", 1_400))
        assertEquals("game", observe("other-game", 2_100))
        assertEquals("game", observe("home", 2_800))
        assertEquals("home", observe("home", 3_500))
    }

    @Test fun missingForegroundHasABoundedGraceAndCancelsPendingSwitch() {
        observe("game", 0)
        observe("home", 700)
        assertEquals("game", observe(null, 1_000))
        // The gap cancels the candidate; a later observation starts a new wait.
        assertEquals("game", observe("home", 1_300))
        assertEquals("game", observe(null, 2_000))
        assertNull(observe(null, 2_800))
        assertEquals("home", observe("home", 2_801))
    }

    @Test fun revokedAccessOrDisabledScenesClearRememberedPackageImmediately() {
        observe("game", 0)
        assertNull(observe("game", 1, enabled = false))
        assertEquals("home", observe("home", 2))
        tracker.clear()
        assertEquals("game", observe("game", 3))
    }

    @Test fun monotonicClockRollbackCannotTrapThePreviousScene() {
        observe("game", 10_000)
        observe("home", 10_100)
        assertEquals("home", observe("home", 5))
        assertNull(observe("game", -1))
    }
}

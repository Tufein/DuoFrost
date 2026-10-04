package io.github.tufein.duofrost.tools

import org.junit.Assert.*
import org.junit.Test

class LedDiagnosticFrameTest {
    @Test fun testsCoverPrimaryColorsWhiteAndEachStick() {
        assertEquals(listOf(0xff0000, 0x00ff00, 0x0000ff, 0xffffff),
            LedDiagnosticFrame.steps.take(4).map { it.left })
        assertEquals(0, LedDiagnosticFrame.steps[4].right)
        assertEquals(0, LedDiagnosticFrame.steps[5].left)
    }
    @Test fun testOutputCannotExceedQuarterBrightness() {
        for (frame in LedDiagnosticFrame.steps) for (zone in 0..3) for (limit in 0..255) {
            val rgb = frame.colorAt(zone, limit)
            assertTrue(((rgb ushr 16) and 255) <= minOf(64, limit))
            assertTrue(((rgb ushr 8) and 255) <= minOf(64, limit))
            assertTrue((rgb and 255) <= minOf(64, limit))
        }
    }
    @Test fun globalLimitsAndMuteStillApplyToDiagnosticColors() {
        val frame = LedDiagnosticFrame.steps[3]
        assertEquals(0x202020, frame.colorAt(0, 32))
        assertEquals(0, frame.colorAt(0, 0))
        assertEquals(0x404040, frame.colorAt(0, Int.MAX_VALUE))
        assertEquals(0, frame.colorAt(0, Int.MIN_VALUE))
    }
    @Test fun diagnosticDoesNotMutateLiveRawFrameForRestoration() {
        val cache = LedFrameCache()
        cache.update(0x804020, 0x208040, 15)
        val overlay = LedDiagnosticFrame.steps[0]
        cache.update(0x401080, 0x108040, 5)
        for (zone in 0..3) assertEquals(0x400000, overlay.colorAt(zone, 255))
        assertEquals(0x401080, cache.colorAt(0))
        assertEquals(0x804020, cache.colorAt(1))
        assertEquals(0x108040, cache.colorAt(2))
        assertEquals(0x208040, cache.colorAt(3))
    }
    @Test fun muteWinsInAllStatesAndUnmuteRestoresConfiguredCeiling() {
        val limits = LedOutputLimits(80, 25, true, 10)
        for (battery in listOf(false, true)) for (screen in listOf(false, true)) {
            assertEquals(0, limits.resolve(true, battery, screen, muted = true))
        }
        assertEquals(204, limits.resolve(true, false, true, muted = false))
        assertEquals(64, limits.resolve(true, true, true, muted = false))
        assertEquals(26, limits.resolve(true, false, false, muted = false))
    }
    @Test fun leavingAllZoneTestBlacksUnwrittenZonesAndPreservesWrittenColors() {
        val cache = LedFrameCache()
        cache.update(0x804020, 0, 3)
        cache.prepareAllZonesForRestore()
        assertEquals(15, cache.zoneMask)
        assertEquals(0x804020, cache.colorAt(0))
        assertEquals(0x804020, cache.colorAt(1))
        assertEquals(0, cache.colorAt(2))
        assertEquals(0, cache.colorAt(3))
        cache.prepareAllZonesForRestore()
        assertEquals(0x804020, cache.colorAt(0))
    }
}

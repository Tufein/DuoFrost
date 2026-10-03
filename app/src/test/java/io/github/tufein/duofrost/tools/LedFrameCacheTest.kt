package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class LedFrameCacheTest {
    @Test fun dualColorFrameKeepsDistinctStickColorsAcrossCapChanges() {
        val frame = LedFrameCache()
        frame.update(0x804020, 0x208040, 15)
        for (zone in 0..1) {
            assertEquals(0x402010, BatterySaverBrightness.limitRgb(frame.colorAt(zone), 64))
            assertEquals(0x804020, BatterySaverBrightness.limitRgb(frame.colorAt(zone), 255))
        }
        for (zone in 2..3) {
            assertEquals(0x104020, BatterySaverBrightness.limitRgb(frame.colorAt(zone), 64))
            assertEquals(0x208040, BatterySaverBrightness.limitRgb(frame.colorAt(zone), 255))
        }
        assertEquals(15, frame.zoneMask)
    }

    @Test fun individualWritesRetainOtherZonesForImmediateRedraw() {
        val frame = LedFrameCache()
        frame.update(0xff0000, 0, 1)
        frame.update(0x00ff00, 0, 2)
        frame.update(0, 0x0000ff, 4)
        frame.update(0, 0xff00ff, 8)
        assertEquals(0xff0000, frame.colorAt(0))
        assertEquals(0x00ff00, frame.colorAt(1))
        assertEquals(0x0000ff, frame.colorAt(2))
        assertEquals(0xff00ff, frame.colorAt(3))
        assertEquals(15, frame.zoneMask)
    }

    @Test fun crossfadeMidpointClearsAllZoneBaselines() {
        val frame = LedFrameCache()
        frame.update(0xff0000, 0x0000ff, 15)
        frame.resetToBlack()
        for (zone in 0..3) assertEquals(0, frame.colorAt(zone))
        assertEquals(15, frame.zoneMask)
    }

    @Test fun emptyFrameAndUnselectedZonesAreNotRedrawn() {
        val frame = LedFrameCache()
        assertEquals(0, frame.zoneMask)
        frame.update(0xff0000, 0x0000ff, 5)
        assertEquals(5, frame.zoneMask)
        assertEquals(0, frame.colorAt(1))
        assertEquals(0, frame.colorAt(3))
    }
}

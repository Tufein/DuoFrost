package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverBrightnessTest {
    @Test fun capRequiresBothUserOptInAndSystemBatterySaver() {
        assertEquals(255, BatterySaverBrightness.resolve(255, false, false))
        assertEquals(255, BatterySaverBrightness.resolve(255, true, false))
        assertEquals(255, BatterySaverBrightness.resolve(255, false, true))
        assertEquals(64, BatterySaverBrightness.resolve(255, true, true))
    }

    @Test fun activeCapNeverBrightensOrExceedsQuarterBrightnessAcrossFullRange() {
        var previous = -1
        for (brightness in 0..255) {
            val result = BatterySaverBrightness.resolve(brightness, true, true)
            assertTrue(result in 0..64)
            assertTrue(result <= brightness)
            assertTrue(result >= previous)
            if (brightness <= 64) assertEquals(brightness, result)
            previous = result
        }
    }

    @Test fun previouslyDerivedAdaptiveAndPluginBrightnessRemainDimmer() {
        // Apply after deriving the screen/adaptive/plugin brightness: a dim
        // source stays dim, while a brighter external source receives the cap.
        assertEquals(0, BatterySaverBrightness.resolve(0, true, true))
        assertEquals(32, BatterySaverBrightness.resolve(32, true, true))
        assertEquals(51, BatterySaverBrightness.resolve(51, true, true))
        assertEquals(64, BatterySaverBrightness.resolve(128, true, true))
        assertEquals(128, BatterySaverBrightness.resolve(128, true, false))
    }

    @Test fun invalidInputsStayWithinHardwareRangeInEveryMode() {
        for (enabled in listOf(false, true)) {
            for (active in listOf(false, true)) {
                assertEquals(0, BatterySaverBrightness.resolve(Int.MIN_VALUE, enabled, active))
                assertEquals(0, BatterySaverBrightness.resolve(-1, enabled, active))
                val maximum = if (enabled && active) 64 else 255
                assertEquals(maximum, BatterySaverBrightness.resolve(256, enabled, active))
                assertEquals(maximum, BatterySaverBrightness.resolve(Int.MAX_VALUE, enabled, active))
            }
        }
    }

    @Test fun renderedBurstAboveTargetStillObeysThePhysicalRgbLimit() {
        // Pip-Boy's static burst can multiply the nominal intensity by three.
        assertEquals(0x400000, BatterySaverBrightness.limitRgb(0xc00000, 64))
        assertEquals(0x004000, BatterySaverBrightness.limitRgb(0x00ff00, 64))
        assertEquals(0x000040, BatterySaverBrightness.limitRgb(0x0000ff, 64))
    }

    @Test fun rgbCapPreservesHueAndLeavesDimColorsUnchanged() {
        assertEquals(0x402010, BatterySaverBrightness.limitRgb(0x804020, 64))
        assertEquals(0x104020, BatterySaverBrightness.limitRgb(0x208040, 64))
        assertEquals(0x201008, BatterySaverBrightness.limitRgb(0x201008, 64))
        assertEquals(0xff402010.toInt(), BatterySaverBrightness.limitRgb(0xff804020.toInt(), 64))
    }

    @Test fun everyRgbChannelIsBoundedAndDisabledCapIsExactlyTransparent() {
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val original = (red shl 16) or (green shl 8) or blue
            assertEquals(original, BatterySaverBrightness.limitRgb(original, 255))
            val limited = BatterySaverBrightness.limitRgb(original, 64)
            assertTrue(((limited ushr 16) and 255) <= 64)
            assertTrue(((limited ushr 8) and 255) <= 64)
            assertTrue((limited and 255) <= 64)
        }
    }

    @Test fun zeroAndInvalidRgbLimitsAreSafe() {
        assertEquals(0, BatterySaverBrightness.limitRgb(0xffffff, 0))
        assertEquals(0, BatterySaverBrightness.limitRgb(0xffffff, Int.MIN_VALUE))
        assertEquals(0xffffff, BatterySaverBrightness.limitRgb(0xffffff, Int.MAX_VALUE))
        assertEquals(0, BatterySaverBrightness.limitRgb(0, 64))
    }
}

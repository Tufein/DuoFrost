package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSamplingTest {
    @Test fun magentaKeepsItsHueWhenBoosted() {
        // Previously a negative hue entered the red sector and lost blue.
        assertEquals(0xffff00ff.toInt(), ScreenSampling.boostSaturation(0xffff00ff.toInt(), 1f))
        // Full saturation: blue = round((192 - 128) / (255 - 128) * 255) = 129.
        assertEquals(0xffff0081.toInt(), ScreenSampling.boostSaturation(0xffff80c0.toInt(), 1f))
    }

    @Test fun fullySaturatedColorsRemainUnchanged() {
        for (color in listOf(0xffff0000, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xff0000ff, 0xffff00ff)) {
            assertEquals(color.toInt(), ScreenSampling.boostSaturation(color.toInt(), 1f))
        }
    }

    @Test fun grayscaleAndZeroBoostRemainUnchanged() {
        for (color in listOf(0xff000000, 0xff808080, 0xffffffff)) {
            assertEquals(color.toInt(), ScreenSampling.boostSaturation(color.toInt(), 1f))
        }
        assertEquals(0xff936abe.toInt(), ScreenSampling.boostSaturation(0xff936abe.toInt(), 0f))
    }

    @Test fun boostPreservesBrightnessAndAlphaAcrossRgbCube() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val result = ScreenSampling.boostSaturation(0x55000000 or (r shl 16) or (g shl 8) or b, 0.7f)
            val channels = listOf((result ushr 16) and 255, (result ushr 8) and 255, result and 255)
            assertEquals(maxOf(r, g, b), channels.maxOrNull()!!)
            assertEquals(0x55, result ushr 24)
            assertTrue(channels.minOrNull()!! <= minOf(r, g, b))
        }
    }

    @Test fun invalidBoostDoesNotCorruptColor() {
        val color = 0xffa080c0.toInt()
        for (amount in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(color, ScreenSampling.boostSaturation(color, amount))
        }
        assertEquals(ScreenSampling.boostSaturation(color, 1f), ScreenSampling.boostSaturation(color, 10f))
    }

    @Test fun customSingleColorStillSamplesAGrid() {
        assertEquals(32 to 18, ScreenSampling.dimensions(true, true, 1920, 1080))
        assertEquals(32 to 18, ScreenSampling.dimensions(true, false, 1920, 1080))
    }

    @Test fun basicModesAndInvalidMetricsHaveBoundedDimensions() {
        assertEquals(1 to 1, ScreenSampling.dimensions(false, true, 1920, 1080))
        assertEquals(2 to 1, ScreenSampling.dimensions(false, false, 1920, 1080))
        assertEquals(32 to 32, ScreenSampling.dimensions(true, true, 0, 1080))
        assertEquals(32 to 1, ScreenSampling.dimensions(true, true, 1920, 0))
        assertEquals(32 to 32, ScreenSampling.dimensions(true, true, 1080, 1920))
    }
}

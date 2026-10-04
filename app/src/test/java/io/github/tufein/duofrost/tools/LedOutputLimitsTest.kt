package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedOutputLimitsTest {
    @Test fun existingInstallsKeepFullOutputAndQuarterBatterySaverCap() {
        val limits = LedOutputLimits.fromStoredValues(emptyMap<String, Any>())
        assertEquals(255, limits.resolve(false, false, true))
        assertEquals(255, limits.resolve(false, false, false))
        assertEquals(64, limits.resolve(true, true, true))
        assertEquals(64, limits.resolve(true, true, false))
    }

    @Test fun batteryCapRequiresOptInAndAndroidBatterySaver() {
        val limits = LedOutputLimits(batterySaverPercent = 10)
        assertEquals(255, limits.resolve(false, false, true))
        assertEquals(255, limits.resolve(true, false, true))
        assertEquals(255, limits.resolve(false, true, true))
        assertEquals(26, limits.resolve(true, true, true))
    }

    @Test fun maximumCapsEveryPowerAndScreenState() {
        val limits = LedOutputLimits(maximumPercent = 10, batterySaverPercent = 80,
            screenOffEnabled = true, screenOffPercent = 50)
        for (enabled in listOf(false, true)) for (active in listOf(false, true)) {
            for (interactive in listOf(false, true)) {
                assertEquals(26, limits.resolve(enabled, active, interactive))
            }
        }
    }

    @Test fun simultaneousCapsChooseTheLowestRatherThanMultiply() {
        val limits = LedOutputLimits(80, 25, true, 10)
        assertEquals(204, limits.resolve(false, false, true))
        assertEquals(64, limits.resolve(true, true, true))
        assertEquals(26, limits.resolve(true, true, false))
        assertEquals(26, limits.resolve(false, false, false))
    }

    @Test fun screenOffCanBlackOutAndWakeRestoresTheApplicableLimit() {
        val limits = LedOutputLimits(80, 25, true, 0)
        assertEquals(0, limits.resolve(true, true, false))
        assertEquals(64, limits.resolve(true, true, true))
        assertEquals(0, limits.resolve(false, false, false))
        assertEquals(204, limits.resolve(false, false, true))
    }

    @Test fun disabledScreenModeIgnoresItsStoredZeroLimit() {
        assertEquals(255, LedOutputLimits(screenOffEnabled = false, screenOffPercent = 0)
            .resolve(false, false, false))
    }

    @Test fun percentageConversionIsMonotonicBoundedAndReachesBothEndpoints() {
        var previous = -1
        for (percent in 0..100) {
            val cap = LedOutputLimits(maximumPercent = percent).resolve(false, false, true)
            assertTrue(cap in 0..255)
            assertTrue(cap >= previous)
            previous = cap
        }
        assertEquals(0, LedOutputLimits(maximumPercent = 0).resolve(false, false, true))
        assertEquals(255, previous)
    }

    @Test fun untrustedStoredTypesDoNotEnableDimmingOrCrash() {
        val limits = LedOutputLimits.fromStoredValues(mapOf(
            LedOutputLimits.PREF_MAXIMUM to "0",
            LedOutputLimits.PREF_BATTERY_SAVER to 0.1f,
            LedOutputLimits.PREF_SCREEN_OFF_ENABLED to "true",
            LedOutputLimits.PREF_SCREEN_OFF to 10L
        ))
        assertEquals(LedOutputLimits(), limits)
    }

    @Test fun storedValuesRoundTripAndExtremePercentagesStayBounded() {
        val limits = LedOutputLimits.fromStoredValues(mapOf(
            LedOutputLimits.PREF_MAXIMUM to Int.MAX_VALUE,
            LedOutputLimits.PREF_BATTERY_SAVER to Int.MIN_VALUE,
            LedOutputLimits.PREF_SCREEN_OFF_ENABLED to true,
            LedOutputLimits.PREF_SCREEN_OFF to 40
        ))
        assertEquals(LedOutputLimits(100, 0, true, 40), limits)
        assertEquals(0, limits.resolve(true, true, true))
        assertEquals(102, limits.resolve(false, false, false))
    }

    @Test fun invalidDirectValuesCannotOverflowRgbRange() {
        assertEquals(255, LedOutputLimits(Int.MAX_VALUE, Int.MAX_VALUE, true, Int.MAX_VALUE)
            .resolve(true, true, false))
        assertEquals(0, LedOutputLimits(Int.MIN_VALUE).resolve(false, false, true))
        assertEquals(0, LedOutputLimits(screenOffEnabled = true, screenOffPercent = Int.MIN_VALUE)
            .resolve(false, false, false))
    }

    @Test fun dimmerSourceAndHueRemainIntactUnderConfiguredCaps() {
        val cap = LedOutputLimits(maximumPercent = 25).resolve(false, false, true)
        assertEquals(0xff402010.toInt(), BatterySaverBrightness.limitRgb(0xff804020.toInt(), cap))
        assertEquals(0x102008, BatterySaverBrightness.limitRgb(0x102008, cap))
    }

    @Test fun screenOffAndWakePreserveFourIndependentRawZoneColors() {
        val cache = LedFrameCache()
        cache.update(0x804020, 0x208040, 15)
        cache.update(0x201008, 0x082010, 5)
        val raw = intArrayOf(0x201008, 0x804020, 0x082010, 0x208040)
        val limits = LedOutputLimits(screenOffEnabled = true)
        for (zone in 0..3) {
            assertEquals(0, BatterySaverBrightness.limitRgb(cache.colorAt(zone), limits.resolve(false, false, false)))
            assertEquals(raw[zone], BatterySaverBrightness.limitRgb(cache.colorAt(zone), limits.resolve(false, false, true)))
            assertEquals(raw[zone], cache.colorAt(zone))
        }
    }
}

package io.github.tufein.duofrost.ui.preview

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectPreviewSamplerTest {
    private val preset = LedPreset("Preview", LedAnimationType.STATIC, PerformanceProfile.HIGH,
        color = 0xFFFF0000.toInt(), rightColor = 0xFF0000FF.toInt(),
        fadeEndColor = 0xFF00FF00.toInt(), fadeEndRightColor = 0xFFFFFFFF.toInt(),
        brightness = 255, speed = 0.5f, smoothness = 0.5f, id = "preview-test")

    @Test fun staticPreservesBothStickColorsAndDoesNotChangeOverTime() {
        val frame = EffectPreviewSampler.sample(preset, 0)
        assertEquals(preset.color, frame.leftTop)
        assertEquals(preset.color, frame.leftBottom)
        assertEquals(preset.rightColor, frame.rightTop)
        assertEquals(preset.rightColor, frame.rightBottom)
        assertEquals(frame, EffectPreviewSampler.sample(preset, Long.MAX_VALUE))
    }

    @Test fun brightnessScalesChannelsInsteadOfOpacity() {
        val frame = EffectPreviewSampler.sample(preset.copy(brightness = 128), 0)
        assertEquals(0xFF800000.toInt(), frame.leftTop)
        assertEquals(0xFF000080.toInt(), frame.rightTop)
    }

    @Test fun zeroBrightnessKeepsEveryEffectDark() {
        LedAnimationType.entries.forEach { type ->
            val frame = EffectPreviewSampler.sample(preset.copy(animationType = type, brightness = 0), 3_213)
            assertEquals(EffectPreviewSampler.Frame(BLACK, BLACK, BLACK, BLACK), frame)
        }
    }

    @Test fun invalidBrightnessAndSpeedAreBoundedAndNegativeTimeStartsAtZero() {
        val breath = preset.copy(animationType = LedAnimationType.BREATH)
        assertEquals(EffectPreviewSampler.sample(breath, 0), EffectPreviewSampler.sample(breath, -1))
        assertEquals(EffectPreviewSampler.sample(breath, 100),
            EffectPreviewSampler.sample(breath.copy(speed = Float.NaN, brightness = 1_000), 100))
        assertEquals(EffectPreviewSampler.Frame(BLACK, BLACK, BLACK, BLACK),
            EffectPreviewSampler.sample(breath.copy(brightness = -1), 100))
        assertEquals(EffectPreviewSampler.sample(breath.copy(speed = 1f), 100),
            EffectPreviewSampler.sample(breath.copy(speed = 100f), 100))
    }

    @Test fun breathMovesSmoothlyAndKeepsLeftRightColorIdentity() {
        val breath = preset.copy(animationType = LedAnimationType.BREATH, speed = 0f)
        val start = EffectPreviewSampler.sample(breath, 0)
        val brighter = EffectPreviewSampler.sample(breath, 2_356)
        assertTrue(red(brighter.leftTop) > red(start.leftTop))
        assertEquals(red(brighter.leftTop), blue(brighter.rightTop))
        assertEquals(0, blue(brighter.leftTop))
        assertEquals(0, red(brighter.rightTop))
    }

    @Test fun rainbowTraversesHueAndUsesOneColorAcrossBothSticks() {
        val rainbow = preset.copy(animationType = LedAnimationType.RAINBOW, speed = 0f)
        val red = EffectPreviewSampler.sample(rainbow, 0)
        val green = EffectPreviewSampler.sample(rainbow, 7_200)
        val blue = EffectPreviewSampler.sample(rainbow, 14_400)
        assertEquals(0xFFFF0000.toInt(), red.leftTop)
        assertEquals(0xFF00FF00.toInt(), green.leftTop)
        assertEquals(0xFF0000FF.toInt(), blue.leftTop)
        assertEquals(green.leftTop, green.rightTop)
    }

    @Test fun fadeReachesIndependentEndColorsAndReturnsToStart() {
        val fade = preset.copy(animationType = LedAnimationType.FADE_TRANSITION, speed = 0f)
        assertEquals(preset.color, EffectPreviewSampler.sample(fade, 0).leftTop)
        val end = EffectPreviewSampler.sample(fade, 3_000)
        assertEquals(preset.fadeEndColor, end.leftTop)
        assertEquals(preset.fadeEndRightColor, end.rightTop)
        assertEquals(EffectPreviewSampler.sample(fade, 0), EffectPreviewSampler.sample(fade, 6_000))
    }

    @Test fun chaseMovesAcrossFourSegmentsWithOneDimTrail() {
        val chase = preset.copy(animationType = LedAnimationType.CHASE, speed = 0f)
        val first = EffectPreviewSampler.sample(chase, 0)
        val second = EffectPreviewSampler.sample(chase, 400)
        assertEquals(preset.color, first.leftTop)
        assertEquals(BLACK, first.leftBottom)
        assertEquals(0xFF000080.toInt(), first.rightBottom)
        assertEquals(preset.color, second.leftBottom)
        assertEquals(0xFF800000.toInt(), second.leftTop)
        assertEquals(first, EffectPreviewSampler.sample(chase, 1_600))
    }

    @Test fun liveInputAndFlashingEffectsNeverAnimateAndDoNotReadInput() {
        listOf(LedAnimationType.AMBIENT, LedAnimationType.AMBIAURORA, LedAnimationType.AUDIO_REACTIVE,
            LedAnimationType.BATTERY_INDICATOR, LedAnimationType.CPU_TEMPERATURE, LedAnimationType.PIPBOY,
            LedAnimationType.PULSE, LedAnimationType.STROBE, LedAnimationType.SPARKLE, LedAnimationType.RAVE)
            .forEach { type ->
                assertFalse(EffectPreviewSampler.canAnimate(type))
                val effect = preset.copy(animationType = type)
                assertEquals(EffectPreviewSampler.sample(effect, 0), EffectPreviewSampler.sample(effect, 8_999))
            }
    }

    @Test fun elapsedTimeIsDeterministicEvenAcrossLongSessions() {
        val animated = preset.copy(animationType = LedAnimationType.RAINBOW)
        val initial = EffectPreviewSampler.sample(animated, 0)
        assertNotEquals(initial, EffectPreviewSampler.sample(animated, 2_000))
        assertEquals(EffectPreviewSampler.sample(animated, Long.MAX_VALUE),
            EffectPreviewSampler.sample(animated, Long.MAX_VALUE))
    }

    private fun red(color: Int) = (color ushr 16) and 255
    private fun blue(color: Int) = color and 255

    companion object { private const val BLACK = -16_777_216 }
}

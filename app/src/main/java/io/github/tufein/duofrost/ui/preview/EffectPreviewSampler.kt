package io.github.tufein.duofrost.ui.preview

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.animations.LedAnimationType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A bounded, deterministic illustration. It has no device, service or sensor dependency. */
internal object EffectPreviewSampler {
    data class Frame(val leftTop: Int, val leftBottom: Int, val rightTop: Int, val rightBottom: Int)

    fun canAnimate(type: LedAnimationType): Boolean = type in setOf(
        LedAnimationType.BREATH, LedAnimationType.RAINBOW,
        LedAnimationType.FADE_TRANSITION, LedAnimationType.CHASE
    )

    fun sample(preset: LedPreset, elapsedMillis: Long): Frame {
        val speed = preset.speed.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.toDouble() ?: 0.5
        val brightness = preset.brightness.coerceIn(0, 255) / 255.0
        val elapsed = elapsedMillis.coerceAtLeast(0).toDouble()
        var left = preset.color
        var right = preset.rightColor
        var factor = brightness
        when (preset.animationType) {
            LedAnimationType.BREATH -> {
                val period = 2.0 * PI * 30.0 / (0.02 + 0.18 * speed)
                factor *= 0.1 + 0.9 * ((sin(phase(elapsed, period) * 2.0 * PI) + 1.0) / 2.0)
            }
            LedAnimationType.RAINBOW -> {
                val period = 360.0 * 30.0 / (0.5 + 4.5 * speed)
                left = hueColor(phase(elapsed, period) * 360.0)
                right = left
            }
            LedAnimationType.FADE_TRANSITION -> {
                val period = 2.0 * 30.0 / (0.01 + 0.04 * speed)
                val progress = 1.0 - abs(phase(elapsed, period) * 2.0 - 1.0)
                left = mix(left, preset.fadeEndColor, progress)
                right = mix(right, preset.fadeEndRightColor, progress)
            }
            LedAnimationType.CHASE -> {
                // Slow the illustration to keep each segment easy to follow on a screen.
                val step = floor(elapsed / (400.0 - 200.0 * speed)).toLong() % 4L
                fun segment(index: Int, color: Int): Int = scale(color, brightness * when (index) {
                    step.toInt() -> 1.0
                    (step.toInt() + 3) % 4 -> 0.5
                    else -> 0.0
                })
                return Frame(segment(0, left), segment(1, left), segment(2, right), segment(3, right))
            }
            else -> Unit // Live-input, random and flashing effects deliberately stay still.
        }
        val leftOutput = scale(left, factor)
        val rightOutput = scale(right, factor)
        return Frame(leftOutput, leftOutput, rightOutput, rightOutput)
    }

    private fun phase(elapsed: Double, period: Double): Double = (elapsed % period) / period

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 255

    private fun scale(color: Int, factor: Double): Int = rgb(
        (channel(color, 16) * factor).roundToInt(),
        (channel(color, 8) * factor).roundToInt(),
        (channel(color, 0) * factor).roundToInt()
    )

    private fun mix(start: Int, end: Int, fraction: Double): Int = rgb(
        (channel(start, 16) + (channel(end, 16) - channel(start, 16)) * fraction).roundToInt(),
        (channel(start, 8) + (channel(end, 8) - channel(start, 8)) * fraction).roundToInt(),
        (channel(start, 0) + (channel(end, 0) - channel(start, 0)) * fraction).roundToInt()
    )

    private fun hueColor(hue: Double): Int {
        val sector = hue / 60.0
        val x = ((1.0 - abs(sector % 2.0 - 1.0)) * 255.0).roundToInt()
        return when (floor(sector).toInt()) {
            0 -> rgb(255, x, 0)
            1 -> rgb(x, 255, 0)
            2 -> rgb(0, 255, x)
            3 -> rgb(0, x, 255)
            4 -> rgb(x, 0, 255)
            else -> rgb(255, 0, x)
        }
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int = (255 shl 24) or
        (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
}

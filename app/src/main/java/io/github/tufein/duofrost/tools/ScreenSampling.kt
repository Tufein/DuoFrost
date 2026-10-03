package io.github.tufein.duofrost.tools

import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure sampling math shared by both Android capture paths. */
internal object ScreenSampling {
    fun dimensions(custom: Boolean, single: Boolean, width: Int, height: Int): Pair<Int, Int> {
        // Keep the grid when averaging one color, otherwise letterbox rejection
        // and vivid-pixel weighting have only a single pixel to work with.
        if (custom) {
            val aspect = height.coerceAtLeast(1).toFloat() / width.coerceAtLeast(1)
            return 32 to (32 * aspect).toInt().coerceIn(1, 32)
        }
        return if (single) 1 to 1 else 2 to 1
    }

    fun boostSaturation(color: Int, boost: Float): Int {
        val amount = if (boost.isFinite()) boost.coerceIn(0f, 1f) else 0f
        if (amount == 0f) return color
        val r = ((color ushr 16) and 255) / 255f
        val g = ((color ushr 8) and 255) / 255f
        val b = (color and 255) / 255f
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        if (delta == 0f) return color
        val rawHue = when (max) {
            r -> (g - b) / delta
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        // Kotlin remainder is negative for red-dominant purple/pink colors.
        val hue = ((rawHue % 6f) + 6f) % 6f
        val saturation = (delta / max * (1f + amount * 2.5f)).coerceAtMost(1f)
        val c = max * saturation
        val x = c * (1f - abs(hue % 2f - 1f))
        val m = max - c
        val (rp, gp, bp) = when {
            hue < 1f -> Triple(c, x, 0f)
            hue < 2f -> Triple(x, c, 0f)
            hue < 3f -> Triple(0f, c, x)
            hue < 4f -> Triple(0f, x, c)
            hue < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (color and -0x1000000) or (channel(rp) shl 16) or (channel(gp) shl 8) or channel(bp)
    }
}

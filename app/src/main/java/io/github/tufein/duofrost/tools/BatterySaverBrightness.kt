package io.github.tufein.duofrost.tools

import kotlin.math.roundToInt

/** A final brightness cap; never increases a dimmer preset or modifies its value. */
object BatterySaverBrightness {
    const val MAX_BRIGHTNESS = 64

    fun resolve(requestedBrightness: Int, enabled: Boolean, batterySaverActive: Boolean): Int {
        val requested = requestedBrightness.coerceIn(0, 255)
        return if (enabled && batterySaverActive) {
            requested.coerceAtMost(MAX_BRIGHTNESS)
        } else {
            requested
        }
    }

    /** Reduce RGB magnitude uniformly, keeping hue and already-dim colors. */
    fun limitRgb(color: Int, brightnessLimit: Int): Int {
        val red = (color ushr 16) and 255
        val green = (color ushr 8) and 255
        val blue = color and 255
        val maximum = maxOf(red, green, blue)
        val limit = brightnessLimit.coerceIn(0, 255)
        if (maximum <= limit) return color
        val scale = limit.toFloat() / maximum
        return (color and 0xff000000.toInt()) or
            ((red * scale).roundToInt() shl 16) or
            ((green * scale).roundToInt() shl 8) or
            (blue * scale).roundToInt()
    }
}

package io.github.tufein.duofrost.tools

/** Four fixed hardware zones, kept independently for event-driven frame redraws. */
internal class LedFrameCache {
    private val colors = IntArray(4)
    var zoneMask: Int = 0
        private set

    fun update(leftColor: Int, rightColor: Int, selectedZones: Int) {
        val selected = selectedZones and 15
        for (zone in 0..3) {
            if ((selected and (1 shl zone)) != 0) {
                colors[zone] = if (zone < 2) leftColor else rightColor
            }
        }
        zoneMask = zoneMask or selected
    }

    fun colorAt(zone: Int): Int = colors[zone]

    /** A temporary all-zone overlay must not leave untouched zones lit. */
    fun prepareAllZonesForRestore() {
        update(0, 0, zoneMask.inv() and 15)
    }

    fun resetToBlack() {
        colors.fill(0)
    }
}

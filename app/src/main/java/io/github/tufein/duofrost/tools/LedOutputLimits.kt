package io.github.tufein.duofrost.tools

import kotlin.math.roundToInt

/** Independent output ceilings. No preset, animation or capture state is changed. */
data class LedOutputLimits(
    val maximumPercent: Int = 100,
    val batterySaverPercent: Int = 25,
    val screenOffEnabled: Boolean = false,
    val screenOffPercent: Int = 0
) {
    fun resolve(batterySaverEnabled: Boolean, batterySaverActive: Boolean, interactive: Boolean, muted: Boolean = false): Int {
        if (muted) return 0
        var percent = maximumPercent.coerceIn(0, 100)
        if (batterySaverEnabled && batterySaverActive) {
            percent = minOf(percent, batterySaverPercent.coerceIn(0, 100))
        }
        if (screenOffEnabled && !interactive) {
            percent = minOf(percent, screenOffPercent.coerceIn(0, 100))
        }
        return (percent * 255f / 100f).roundToInt()
    }

    companion object {
        const val PREF_MAXIMUM = "led_output_maximum_percent"
        const val PREF_BATTERY_SAVER = "led_output_battery_saver_percent"
        const val PREF_SCREEN_OFF_ENABLED = "led_output_screen_off_enabled"
        const val PREF_SCREEN_OFF = "led_output_screen_off_percent"

        /** Older installs and incorrectly typed imported values keep safe defaults. */
        fun fromStoredValues(values: Map<String, *>): LedOutputLimits = LedOutputLimits(
            maximumPercent = (values[PREF_MAXIMUM] as? Int ?: 100).coerceIn(0, 100),
            batterySaverPercent = (values[PREF_BATTERY_SAVER] as? Int ?: 25).coerceIn(0, 100),
            screenOffEnabled = values[PREF_SCREEN_OFF_ENABLED] as? Boolean ?: false,
            screenOffPercent = (values[PREF_SCREEN_OFF] as? Int ?: 0).coerceIn(0, 100)
        )
    }
}

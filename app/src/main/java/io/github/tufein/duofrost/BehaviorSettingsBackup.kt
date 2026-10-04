package io.github.tufein.duofrost

/** Explicit transferable settings. Runtime state and Android grants are excluded. */
object BehaviorSettingsBackup {
    private val booleanKeys = setOf(
        "keep_running_enabled", "auto_start_heimdall", "adaptive_brightness_enabled",
        "battery_saver_brightness_enabled", "led_output_screen_off_enabled",
        "persistent_notification_enabled", "battery_override_when_plugged",
        "low_battery_alert_enabled", "disable_low_battery_alert_while_charging",
        "ambilight_use_media_projection", "thor_bottom_screen", "thor_ambient_bottom_screen",
        "external_api_enabled"
    )
    private val integerRanges = mapOf(
        "led_output_maximum_percent" to 0..100,
        "led_output_battery_saver_percent" to 0..100,
        "led_output_screen_off_percent" to 0..100,
        "low_battery_alert_threshold" to 0..100
    )
    val keys: Set<String> = booleanKeys + integerRanges.keys

    fun decode(key: String, type: String, value: Any?): Any? = when {
        key in booleanKeys && type == "boolean" && value is Boolean -> value
        key in integerRanges && type == "int" && value is Int && value in integerRanges.getValue(key) -> value
        else -> null
    }
}

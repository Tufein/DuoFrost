package io.github.tufein.duofrost.services

import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONObject

/** Only durable user configuration belongs here, never Intent parcels or leases. */
data class ServiceRecoveryConfiguration(val values: Map<String, Any>)

object ServiceRecoveryCodec {
    private const val SCHEMA = "duofrost_service_configuration"
    private const val VERSION = 1
    private const val MAX_JSON_LENGTH = 16_384
    private const val DEFAULT_FADE_COLOR = -16_711_681 // opaque cyan

    private val colorKeys = setOf(
        "animationColor", "animationRightColor", "fadeEndColor", "fadeEndRightColor",
        "batteryLowColorOverride", "batteryMidColorOverride", "batteryHighColorOverride",
        "cpuCoolColorOverride", "cpuWarmColorOverride", "cpuHotColorOverride"
    )
    private val floatKeys = setOf("speed", "smoothness", "sensitivity", "saturationBoost")
    private val booleanKeys = setOf(
        "useCustomSampling", "useSingleColor", "breatheWhenCharging", "indicateChargingSpeed",
        "flashWhenReady", "batteryOverrideWhenPlugged", "lowBatteryAlertEnabled",
        "disableLowBatteryAlertWhileCharging", "persistentNotification", "adaptiveBrightness",
        "batterySaverBrightness"
    )
    val allowedKeys: Set<String> = colorKeys + floatKeys + booleanKeys + setOf(
        "animationType", "performanceProfile", "brightness", "lowBatteryAlertThreshold", "ambientDisplayId"
    )

    fun snapshot(input: Map<String, Any?>): ServiceRecoveryConfiguration? {
        val clean = sanitise(input) ?: return null
        if (!clean.containsKey("animationType")) return null
        val color = clean["animationColor"] ?: -1
        val fade = clean["fadeEndColor"] ?: DEFAULT_FADE_COLOR
        val defaults = mapOf<String, Any>(
            "performanceProfile" to PerformanceProfile.HIGH.name,
            "animationColor" to color,
            "animationRightColor" to color,
            "fadeEndColor" to fade,
            "fadeEndRightColor" to fade,
            "brightness" to 255,
            "speed" to 0.5f,
            "smoothness" to 0.5f,
            "sensitivity" to 0.5f,
            "saturationBoost" to 0f,
            "ambientDisplayId" to 0
        )
        return ServiceRecoveryConfiguration(defaults + clean)
    }

    fun merge(
        current: ServiceRecoveryConfiguration?,
        updates: Map<String, Any?>
    ): ServiceRecoveryConfiguration? {
        if (current == null) return null
        val clean = sanitise(updates) ?: return null
        return ServiceRecoveryConfiguration(current.values + clean)
    }

    fun encode(configuration: ServiceRecoveryConfiguration): String = JSONObject().apply {
        put("schema", SCHEMA)
        put("version", VERSION)
        put("configuration", JSONObject(configuration.values))
    }.toString()

    fun decode(raw: String?): ServiceRecoveryConfiguration? {
        if (raw.isNullOrBlank() || raw.length > MAX_JSON_LENGTH) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optString("schema") != SCHEMA || root.opt("version") != VERSION) return null
            val config = root.optJSONObject("configuration") ?: return null
            val input = allowedKeys.filter { config.has(it) }.associateWith { config.opt(it) }
            snapshot(input)
        }.getOrNull()
    }

    private fun sanitise(input: Map<String, Any?>): Map<String, Any>? {
        val clean = mutableMapOf<String, Any>()
        for (key in allowedKeys) {
            if (!input.containsKey(key)) continue
            val value = input[key] ?: return null
            val typed = when {
                key == "animationType" -> (value as? String)
                    ?.let(LedAnimationType::fromStoredName)?.name
                key == "performanceProfile" -> (value as? String)?.takeIf { name ->
                    PerformanceProfile.values().any { it.name == name }
                }
                key in booleanKeys -> value as? Boolean
                key in floatKeys -> (value as? Number)?.toDouble()?.takeIf {
                    it.isFinite() && it in 0.0..1.0
                }?.toFloat()
                else -> integer(value)?.takeIf {
                    when (key) {
                        "brightness" -> it in 0..255
                        "lowBatteryAlertThreshold" -> it in 1..100
                        "ambientDisplayId" -> it in 0..65_535
                        else -> key in colorKeys
                    }
                }
            } ?: return null
            clean[key] = typed
        }
        return clean.toMap()
    }

    private fun integer(value: Any): Int? {
        val number = value as? Number ?: return null
        val precise = number.toDouble()
        if (!precise.isFinite() || precise < Int.MIN_VALUE || precise > Int.MAX_VALUE) return null
        val int = number.toInt()
        return int.takeIf { precise == int.toDouble() }
    }
}

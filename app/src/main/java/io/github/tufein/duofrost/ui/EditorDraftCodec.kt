package io.github.tufein.duofrost.ui

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONObject
import org.json.JSONTokener

/** Temporary editor state only: never preset ownership, hardware state or capture grants. */
object EditorDraftCodec {
    private const val SCHEMA = "duofrost_editor_draft"
    private const val VERSION = 1
    private const val MAX_BYTES = 16 * 1024
    private const val MAX_NAME_LENGTH = 128

    fun encode(preset: LedPreset): String = JSONObject().apply {
        put("schema", SCHEMA)
        put("version", VERSION)
        put("name", preset.name)
        put("animationType", preset.animationType.name)
        put("performanceProfile", preset.performanceProfile.name)
        put("color", preset.color)
        put("rightColor", preset.rightColor)
        put("fadeEndColor", preset.fadeEndColor)
        put("fadeEndRightColor", preset.fadeEndRightColor)
        put("brightness", preset.brightness)
        put("speed", preset.speed.toDouble())
        put("smoothness", preset.smoothness.toDouble())
        put("sensitivity", preset.sensitivity.toDouble())
        put("saturationBoost", preset.saturationBoost.toDouble())
        put("useCustomSampling", preset.useCustomSampling)
        put("useSingleColor", preset.useSingleColor)
        put("breatheWhenCharging", preset.breatheWhenCharging)
        put("indicateChargingSpeed", preset.indicateChargingSpeed)
        put("flashWhenReady", preset.flashWhenReady)
        put("batteryLowColorOverride", preset.batteryLowColorOverride ?: JSONObject.NULL)
        put("batteryMidColorOverride", preset.batteryMidColorOverride ?: JSONObject.NULL)
        put("batteryHighColorOverride", preset.batteryHighColorOverride ?: JSONObject.NULL)
        put("cpuCoolColorOverride", preset.cpuCoolColorOverride ?: JSONObject.NULL)
        put("cpuWarmColorOverride", preset.cpuWarmColorOverride ?: JSONObject.NULL)
        put("cpuHotColorOverride", preset.cpuHotColorOverride ?: JSONObject.NULL)
    }.toString()

    fun decode(raw: String?): LedPreset? {
        if (raw.isNullOrBlank() || raw.length > MAX_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return null
        }
        return runCatching {
            val tokener = JSONTokener(raw)
            val draft = tokener.nextValue() as? JSONObject ?: return null
            if (tokener.nextClean() != '\u0000') return null
            if (draft.get("schema") != SCHEMA || integer(draft, "version") != VERSION) return null
            val name = draft.get("name") as? String ?: return null
            if (name.length > MAX_NAME_LENGTH) return null

            LedPreset(
                name = name,
                animationType = LedAnimationType.valueOf(draft.get("animationType") as String),
                performanceProfile = PerformanceProfile.valueOf(draft.get("performanceProfile") as String),
                color = integer(draft, "color"),
                rightColor = integer(draft, "rightColor"),
                fadeEndColor = integer(draft, "fadeEndColor"),
                fadeEndRightColor = integer(draft, "fadeEndRightColor"),
                brightness = integer(draft, "brightness").also { require(it in 0..255) },
                speed = fraction(draft, "speed"),
                smoothness = fraction(draft, "smoothness"),
                sensitivity = fraction(draft, "sensitivity"),
                saturationBoost = fraction(draft, "saturationBoost"),
                useCustomSampling = draft.get("useCustomSampling") as Boolean,
                useSingleColor = draft.get("useSingleColor") as Boolean,
                breatheWhenCharging = draft.get("breatheWhenCharging") as Boolean,
                indicateChargingSpeed = draft.get("indicateChargingSpeed") as Boolean,
                flashWhenReady = draft.get("flashWhenReady") as Boolean,
                batteryLowColorOverride = nullableColor(draft, "batteryLowColorOverride"),
                batteryMidColorOverride = nullableColor(draft, "batteryMidColorOverride"),
                batteryHighColorOverride = nullableColor(draft, "batteryHighColorOverride"),
                cpuCoolColorOverride = nullableColor(draft, "cpuCoolColorOverride"),
                cpuWarmColorOverride = nullableColor(draft, "cpuWarmColorOverride"),
                cpuHotColorOverride = nullableColor(draft, "cpuHotColorOverride")
            )
        }.getOrNull()
    }

    private fun integer(draft: JSONObject, key: String): Int {
        val value = (draft.get(key) as Number).toDouble()
        require(value.isFinite() && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
        require(value % 1.0 == 0.0)
        return value.toInt()
    }

    private fun fraction(draft: JSONObject, key: String): Float {
        val value = (draft.get(key) as Number).toDouble()
        require(value.isFinite() && value in 0.0..1.0)
        return value.toFloat()
    }

    private fun nullableColor(draft: JSONObject, key: String): Int? =
        if (draft.get(key) === JSONObject.NULL) null else integer(draft, key)
}

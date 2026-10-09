package io.github.tufein.duofrost

import io.github.tufein.duofrost.animations.FadeTransitionAnimation
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONObject

/** The shared storage format. Unknown fields survive edits of known properties. */
object PresetCodec {
    fun decode(obj: JSONObject, index: Int = 0): LedPreset {
        val type = LedAnimationType.fromStoredName(obj.optString("animationType"))
            ?: LedAnimationType.STATIC
        val profile = runCatching {
            PerformanceProfile.valueOf(obj.optString("performanceProfile", PerformanceProfile.HIGH.name))
        }.getOrDefault(PerformanceProfile.HIGH)
        val color = obj.optInt("color", -1)
        val fadeEndColor = obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)
        return LedPreset(
            name = obj.optString("name", "Preset ${index + 1}"),
            animationType = type,
            performanceProfile = profile,
            color = color,
            rightColor = obj.optInt("rightColor", color),
            fadeEndColor = fadeEndColor,
            fadeEndRightColor = obj.optInt("fadeEndRightColor", fadeEndColor),
            brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
            speed = fraction(obj, "speed", 0.5),
            smoothness = fraction(obj, "smoothness", 0.5),
            sensitivity = fraction(obj, "sensitivity", 0.5),
            saturationBoost = fraction(obj, "saturationBoost", 0.0),
            useCustomSampling = obj.optBoolean("useCustomSampling", false),
            useSingleColor = obj.optBoolean("useSingleColor", false),
            breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
            indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
            flashWhenReady = obj.optBoolean("flashWhenReady", false),
            batteryLowColorOverride = optionalInt(obj, "batteryLowColorOverride"),
            batteryMidColorOverride = optionalInt(obj, "batteryMidColorOverride"),
            batteryHighColorOverride = optionalInt(obj, "batteryHighColorOverride"),
            cpuCoolColorOverride = optionalInt(obj, "cpuCoolColorOverride"),
            cpuWarmColorOverride = optionalInt(obj, "cpuWarmColorOverride"),
            cpuHotColorOverride = optionalInt(obj, "cpuHotColorOverride"),
            isAppProfileDefault = obj.optBoolean("isAppProfileDefault", false),
            ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
            icon = PresetIcon.fromStoredName(obj.optString("icon", PresetIcon.defaultFor(type).name)),
            customEmoji = optionalString(obj, "customEmoji"),
            customImageFileName = optionalString(obj, "customImageFileName"),
            appIconPackageName = optionalString(obj, "appIconPackageName"),
            ownerPackage = optionalString(obj, "ownerPackage"),
            id = (obj.opt("id") as? String)?.takeIf(PresetIdentity::isValid) ?: PresetIdentity.newId()
        )
    }

    fun encode(preset: LedPreset, existing: JSONObject? = null): JSONObject {
        val obj = existing?.let { JSONObject(it.toString()) } ?: JSONObject()
        obj.put("id", preset.id)
        obj.put("name", preset.name)
        obj.put("animationType", preset.animationType.name)
        obj.put("performanceProfile", preset.performanceProfile.name)
        obj.put("color", preset.color)
        obj.put("rightColor", preset.rightColor)
        obj.put("fadeEndColor", preset.fadeEndColor)
        obj.put("fadeEndRightColor", preset.fadeEndRightColor)
        obj.put("brightness", preset.brightness)
        obj.put("speed", preset.speed.toDouble())
        obj.put("smoothness", preset.smoothness.toDouble())
        obj.put("sensitivity", preset.sensitivity.toDouble())
        obj.put("saturationBoost", preset.saturationBoost.toDouble())
        obj.put("useCustomSampling", preset.useCustomSampling)
        obj.put("useSingleColor", preset.useSingleColor)
        obj.put("breatheWhenCharging", preset.breatheWhenCharging)
        obj.put("indicateChargingSpeed", preset.indicateChargingSpeed)
        obj.put("flashWhenReady", preset.flashWhenReady)
        obj.putOptional("batteryLowColorOverride", preset.batteryLowColorOverride)
        obj.putOptional("batteryMidColorOverride", preset.batteryMidColorOverride)
        obj.putOptional("batteryHighColorOverride", preset.batteryHighColorOverride)
        obj.putOptional("cpuCoolColorOverride", preset.cpuCoolColorOverride)
        obj.putOptional("cpuWarmColorOverride", preset.cpuWarmColorOverride)
        obj.putOptional("cpuHotColorOverride", preset.cpuHotColorOverride)
        obj.put("isAppProfileDefault", preset.isAppProfileDefault)
        obj.put("ragnarokAccepted", preset.ragnarokAccepted)
        obj.put("icon", preset.icon.name)
        obj.putOptional("customEmoji", preset.customEmoji)
        obj.putOptional("customImageFileName", preset.customImageFileName)
        obj.putOptional("appIconPackageName", preset.appIconPackageName)
        obj.putOptional("ownerPackage", preset.ownerPackage)
        return obj
    }

    private fun optionalString(obj: JSONObject, key: String): String? =
        (obj.opt(key) as? String)?.takeIf { it.isNotBlank() }

    private fun optionalInt(obj: JSONObject, key: String): Int? =
        obj.optInt(key).takeIf { obj.has(key) && !obj.isNull(key) }

    private fun fraction(obj: JSONObject, key: String, default: Double): Float {
        val value = obj.optDouble(key, default)
        return if (value.isFinite()) value.coerceIn(0.0, 1.0).toFloat() else default.toFloat()
    }

    private fun JSONObject.putOptional(key: String, value: Any?) {
        if (value == null) remove(key) else put(key, value)
    }
}

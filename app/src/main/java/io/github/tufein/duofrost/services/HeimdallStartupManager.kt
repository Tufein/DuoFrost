package io.github.tufein.duofrost.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.tufein.duofrost.animations.FadeTransitionAnimation
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import io.github.tufein.duofrost.tools.DeviceInfo
import org.json.JSONArray
import org.json.JSONObject

object HeimdallStartupManager {
    private const val PREF_KEY_AUTO_START_HEIMDALL = "auto_start_heimdall"
    private const val PREF_KEY_PRESETS = "presets_json"
    private const val PREF_KEY_LAST_PRESET = "last_preset_name"
    private const val PREF_KEY_APP_PROFILE_ENABLED = "auto_switch_enabled"
    private val PALETTE_KEYS = listOf(
        "batteryLowColorOverride", "batteryMidColorOverride", "batteryHighColorOverride",
        "cpuCoolColorOverride", "cpuWarmColorOverride", "cpuHotColorOverride"
    )

    fun isAutoStartEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_KEY_AUTO_START_HEIMDALL, false)
    }

    fun setAutoStartEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_AUTO_START_HEIMDALL, enabled).apply()
    }

    enum class StartupSkipReason {
        NO_PRESET_AVAILABLE
    }

    data class StartupDecision(
        val serviceIntent: Intent?,
        val skipReason: StartupSkipReason? = null
    )

    fun buildStartupServiceIntent(context: Context, prefs: SharedPreferences): Intent? {
        return buildStartupDecision(context, prefs).serviceIntent
    }

    fun buildServiceIntentForPreset(
        context: Context,
        prefs: SharedPreferences,
        presetName: String
    ): Intent? {
        val raw = prefs.getString(PREF_KEY_PRESETS, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("name") == presetName) {
                return buildServiceIntent(context, prefs, parsePreset(obj))
            }
        }
        return null
    }

    fun buildStartupDecision(context: Context, prefs: SharedPreferences): StartupDecision {
        val preset = loadStartupPreset(prefs)
        if (preset == null) {
            val last = ServiceRecoveryStore.buildLastConfigurationIntent(context, prefs)
            return StartupDecision(last, if (last == null) StartupSkipReason.NO_PRESET_AVAILABLE else null)
        }

        return StartupDecision(serviceIntent = buildServiceIntent(context, prefs, preset))
    }

    fun requiresProjectionConsent(intent: Intent, prefs: SharedPreferences): Boolean {
        val type = LedAnimationType.fromStoredName(intent.getStringExtra("animationType")) ?: return false
        return type.needsMediaProjection ||
            (type == LedAnimationType.AMBIENT && prefs.getBoolean(
                LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION
            ))
    }

    private fun buildServiceIntent(
        context: Context,
        prefs: SharedPreferences,
        preset: StartupPreset
    ): Intent {
        return Intent(context, LEDService::class.java).apply {
            putExtra("animationType", preset.animationType.name)
            putExtra("performanceProfile", preset.performanceProfile.name)
            putExtra("animationColor", preset.color)
            putExtra("animationRightColor", preset.rightColor)
            putExtra(LEDService.EXTRA_FADE_END_COLOR, preset.fadeEndColor)
            putExtra(LEDService.EXTRA_FADE_END_RIGHT_COLOR, preset.fadeEndRightColor)
            putExtra("brightness", preset.brightness)
            putExtra("speed", preset.speed)
            putExtra("smoothness", preset.smoothness)
            putExtra("sensitivity", preset.sensitivity)
            putExtra("saturationBoost", preset.saturationBoost)
            putExtra("useCustomSampling", preset.useCustomSampling)
            putExtra("useSingleColor", preset.useSingleColor)
            putExtra("breatheWhenCharging", preset.breatheWhenCharging)
            putExtra("indicateChargingSpeed", preset.indicateChargingSpeed)
            putExtra("flashWhenReady", preset.flashWhenReady)
            preset.palette.forEach { (key, color) -> putExtra(key, color) }
            putExtra("ambientDisplayId", resolveAmbientDisplay(context, prefs))
            ServiceRecoveryStore.applyCurrentGlobalSettings(this, prefs)
        }
    }

    private fun resolveAmbientDisplay(context: Context, prefs: SharedPreferences): Int {
        if (!DeviceInfo.isAynThor || !prefs.getBoolean("thor_ambient_bottom_screen", false)) {
            return Display.DEFAULT_DISPLAY
        }
        return context.getSystemService(DisplayManager::class.java)?.displays
            ?.map { it.displayId }?.filter { it != Display.DEFAULT_DISPLAY }?.minOrNull()
            ?: Display.DEFAULT_DISPLAY
    }

    private fun loadStartupPreset(prefs: SharedPreferences): StartupPreset? {
        val raw = prefs.getString(PREF_KEY_PRESETS, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        if (array.length() == 0) return null

        val appProfileEnabled = prefs.getBoolean(PREF_KEY_APP_PROFILE_ENABLED, false)
        if (appProfileEnabled) {
            val defaultObj = findDefaultPresetObject(array)
            if (defaultObj != null) {
                return parsePreset(defaultObj)
            }
        }

        val lastPresetName = prefs.getString(PREF_KEY_LAST_PRESET, null)
        val presetObj = findPresetObject(array, lastPresetName) ?: return null
        return parsePreset(presetObj)
    }

    private fun findDefaultPresetObject(array: JSONArray): JSONObject? {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("isAppProfileDefault", false)) {
                return obj
            }
        }
        return null
    }

    private fun findPresetObject(array: JSONArray, name: String?): JSONObject? {
        if (name != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("name") == name) {
                    return obj
                }
            }
        }

        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { return it }
        }
        return null
    }

    private fun parsePreset(obj: JSONObject): StartupPreset {
        val animationType = runCatching {
            LedAnimationType.fromStoredName(obj.optString("animationType")) ?: LedAnimationType.STATIC
        }.getOrDefault(LedAnimationType.STATIC)

        val performanceProfile = runCatching {
            PerformanceProfile.valueOf(
                obj.optString("performanceProfile", PerformanceProfile.HIGH.name)
            )
        }.getOrDefault(PerformanceProfile.HIGH)

        val color = obj.optInt("color", Color.WHITE)

        return StartupPreset(
            animationType = animationType,
            performanceProfile = performanceProfile,
            color = color,
            rightColor = obj.optInt("rightColor", color),
            fadeEndColor = obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR),
            fadeEndRightColor = obj.optInt("fadeEndRightColor", obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)),
            brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
            speed = ratio(obj, "speed", 0.5f),
            smoothness = ratio(obj, "smoothness", 0.5f),
            sensitivity = ratio(obj, "sensitivity", 0.5f),
            saturationBoost = ratio(obj, "saturationBoost", 0f),
            useCustomSampling = obj.optBoolean("useCustomSampling", false),
            useSingleColor = obj.optBoolean("useSingleColor", false),
            breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
            indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
            flashWhenReady = obj.optBoolean("flashWhenReady", false),
            palette = PALETTE_KEYS.filter { obj.has(it) && !obj.isNull(it) }.associateWith { obj.optInt(it) }
        )
    }

    private fun ratio(obj: JSONObject, key: String, fallback: Float): Float =
        obj.optDouble(key, fallback.toDouble()).toFloat().takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f) ?: fallback

    private data class StartupPreset(
        val animationType: LedAnimationType,
        val performanceProfile: PerformanceProfile,
        val color: Int,
        val rightColor: Int,
        val fadeEndColor: Int,
        val fadeEndRightColor: Int,
        val brightness: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val saturationBoost: Float,
        val useCustomSampling: Boolean,
        val useSingleColor: Boolean,
        val breatheWhenCharging: Boolean,
        val indicateChargingSpeed: Boolean,
        val flashWhenReady: Boolean,
        val palette: Map<String, Int>
    )
}

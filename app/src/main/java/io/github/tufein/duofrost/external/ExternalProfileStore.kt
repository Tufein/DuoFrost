package io.github.tufein.duofrost.external

import android.content.SharedPreferences
import io.github.tufein.duofrost.PresetIcon
import io.github.tufein.duofrost.PresetIdentity
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes the same `presets_json` blob PresetController owns, but
 * only for presets tagged with `ownerPackage`. The in-memory list PresetController
 * keeps is refreshed on the next MainActivity.onResume sweep, so callers don't
 * need to coordinate with the UI process for installs to become visible.
 */
object ExternalProfileStore {

    private const val PREF_PRESETS = "presets_json"

    fun installManagedPreset(
        prefs: SharedPreferences,
        command: ExternalApiCommand.InstallProfile
    ): Boolean = synchronized(prefs) {
        if (readStoredPresets(prefs) == null) return@synchronized false
        PresetRepository(prefs).ensureIds()
        val list = readStoredPresets(prefs) ?: return@synchronized false
        val existingIndex = (0 until list.length()).firstOrNull { index ->
            val obj = list.optJSONObject(index)
            obj?.optString("name") == command.profileName && obj.optString("ownerPackage") == command.callerPackage
        } ?: -1
        if (existingIndex >= 0 && !command.replaceIfExists) return@synchronized false

        val serialized = command.toPresetJson(if (existingIndex >= 0) list.optJSONObject(existingIndex) else null)
        if (existingIndex >= 0) list.put(existingIndex, serialized) else list.put(serialized)
        saveList(prefs, list)
        true
    }

    fun uninstallManagedPreset(
        prefs: SharedPreferences,
        callerPackage: String,
        profileName: String
    ): Boolean = synchronized(prefs) {
        val list = readStoredPresets(prefs) ?: return@synchronized false
        val before = list.length()
        for (index in list.length() - 1 downTo 0) {
            val obj = list.optJSONObject(index) ?: continue
            if (obj.optString("name") == profileName && obj.optString("ownerPackage") == callerPackage) list.remove(index)
        }
        if (list.length() == before) return@synchronized false
        saveList(prefs, list)
        true
    }

    fun removePresetsOwnedBy(prefs: SharedPreferences, pkg: String): List<String> = synchronized(prefs) {
        val list = readStoredPresets(prefs) ?: return@synchronized emptyList()
        val removed = mutableListOf<String>()
        val retained = JSONArray()
        for (index in 0 until list.length()) {
            val obj = list.optJSONObject(index)
            if (obj?.optString("ownerPackage") == pkg) {
                removed.add(obj.optString("name"))
            } else retained.put(list.opt(index))
        }
        if (removed.isNotEmpty()) saveList(prefs, retained)
        removed
    }

    private fun ExternalApiCommand.InstallProfile.toPresetJson(existing: JSONObject? = null): JSONObject =
        (existing?.let { JSONObject(it.toString()) } ?: JSONObject()).apply {
        put("id", existing?.optString("id")?.takeIf(PresetIdentity::isValid) ?: PresetIdentity.newId())
        put("name", profileName)
        put("animationType", effect.name)
        put("performanceProfile", PerformanceProfile.HIGH.name)
        put("color", color)
        put("rightColor", colorRight)
        put("brightness", intensity)
        put("speed", speed.toDouble())
        put("smoothness", smoothness.toDouble())
        put("sensitivity", sensitivity.toDouble())
        put("saturationBoost", saturationBoost.toDouble())
        put("useCustomSampling", useCustomSampling)
        put("useSingleColor", useSingleColor)
        put("breatheWhenCharging", breatheWhenCharging)
        put("indicateChargingSpeed", indicateChargingSpeed)
        put("flashWhenReady", flashWhenReady)
        putOptional("batteryLowColorOverride", batteryLowColor)
        putOptional("batteryMidColorOverride", batteryMidColor)
        putOptional("batteryHighColorOverride", batteryHighColor)
        putOptional("cpuCoolColorOverride", cpuCoolColor)
        putOptional("cpuWarmColorOverride", cpuWarmColor)
        putOptional("cpuHotColorOverride", cpuHotColor)
        put("isAppProfileDefault", false)
        put("ragnarokAccepted", false)
        put("icon", existing?.optString("icon")?.takeIf { it.isNotBlank() } ?: PresetIcon.defaultFor(effect).name)
        put("ownerPackage", callerPackage)
    }

    private fun JSONObject.putOptional(key: String, value: Any?) {
        if (value == null) remove(key) else put(key, value)
    }

    /** Missing is an empty library; malformed or differently typed data must never be replaced. */
    internal fun readStoredPresets(prefs: SharedPreferences): JSONArray? {
        val raw = try { prefs.getString(PREF_PRESETS, null) } catch (_: ClassCastException) { return null }
        return if (raw == null) JSONArray() else runCatching { JSONArray(raw) }.getOrNull()
    }

    private fun saveList(prefs: SharedPreferences, list: JSONArray) {
        prefs.edit().putString(PREF_PRESETS, list.toString()).apply()
    }
}

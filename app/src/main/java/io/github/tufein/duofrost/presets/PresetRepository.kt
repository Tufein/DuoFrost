package io.github.tufein.duofrost

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Persisted IDs and one cached decoding of the preset list shared by scenes and UI. */
class PresetRepository(private val prefs: SharedPreferences) {
    private var cachedRaw: String? = null
    private var identityCheckedRaw: String? = null
    private var cachedPresets: List<LedPreset> = emptyList()

    fun ensureIds(): Boolean = synchronized(prefs) {
        val raw = prefs.getString(PREF_PRESETS, null) ?: return@synchronized false
        if (raw == identityCheckedRaw) return@synchronized false
        val normalized = PresetIdentity.normalizeJson(raw) ?: run {
            identityCheckedRaw = raw
            return@synchronized false
        }
        identityCheckedRaw = normalized
        if (normalized == raw) return@synchronized false
        prefs.edit().putString(PREF_PRESETS, normalized).apply()
        true
    }

    fun list(): List<LedPreset> = synchronized(prefs) {
        ensureIds()
        val raw = prefs.getString(PREF_PRESETS, null)
        if (raw == cachedRaw) return@synchronized cachedPresets
        val array = raw?.let { runCatching { JSONArray(it) }.getOrNull() }
        cachedPresets = if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(PresetCodec.decode(it, index)) }
            }
        }
        cachedRaw = raw
        cachedPresets
    }

    fun findById(id: String): LedPreset? = list().firstOrNull { it.id == id }

    /** Preserve unknown JSON fields by identity during ordinary preset edits. */
    fun save(presets: List<LedPreset>, preserveUnknownFields: Boolean = true): List<LedPreset> = synchronized(prefs) {
        ensureIds()
        val oldRaw = prefs.getString(PREF_PRESETS, null)
        val oldArray = oldRaw?.let { runCatching { JSONArray(it) }.getOrNull() }
        // Never replace malformed stored data with an empty/default configuration.
        if (oldRaw != null && oldArray == null) return@synchronized presets.toList()
        val oldById = mutableMapOf<String, JSONObject>()
        if (preserveUnknownFields && oldArray != null) {
            for (index in 0 until oldArray.length()) {
                oldArray.optJSONObject(index)?.let { oldById[it.optString("id")] = it }
            }
        }
        val normalized = PresetIdentity.normalizeImported(presets)
        val array = JSONArray()
        normalized.forEach { array.put(PresetCodec.encode(it, oldById[it.id])) }
        if (preserveUnknownFields && oldArray != null) {
            for (index in 0 until oldArray.length()) {
                if (oldArray.optJSONObject(index) == null) array.put(oldArray.opt(index))
            }
        }
        val raw = array.toString()
        prefs.edit().putString(PREF_PRESETS, raw).apply()
        identityCheckedRaw = raw
        cachedRaw = null
        normalized
    }

    companion object {
        private const val PREF_PRESETS = "presets_json"
    }
}

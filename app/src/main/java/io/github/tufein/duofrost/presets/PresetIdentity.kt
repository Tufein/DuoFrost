package io.github.tufein.duofrost

import org.json.JSONArray
import java.util.UUID

/** Identity is independent of a preset's user-editable name. */
object PresetIdentity {
    private val validId = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun newId(): String = UUID.randomUUID().toString()

    fun isValid(id: String?): Boolean = id != null && validId.matches(id)

    /** Patch only IDs; leave every other field and unrecognised array entry intact. */
    fun normalizeJson(raw: String): String? {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val used = mutableSetOf<String>()
        var changed = false
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val existing = obj.opt("id") as? String
            if (isValid(existing) && used.add(existing!!)) continue
            val id = freshId(used)
            obj.put("id", id)
            used.add(id)
            changed = true
        }
        return if (changed) array.toString() else raw
    }

    /** Appending a bundle must never make a scene point to two different presets. */
    fun normalizeImported(
        presets: List<LedPreset>,
        reservedIds: Set<String> = emptySet()
    ): List<LedPreset> {
        val used = reservedIds.toMutableSet()
        return presets.map { preset ->
            if (isValid(preset.id) && used.add(preset.id)) {
                preset
            } else {
                val id = freshId(used)
                used.add(id)
                preset.copy(id = id)
            }
        }
    }

    private fun freshId(used: Set<String>): String {
        var id = newId()
        while (id in used) id = newId()
        return id
    }
}

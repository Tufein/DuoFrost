package io.github.tufein.duofrost.ui.preview

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetCodec
import io.github.tufein.duofrost.PresetIdentity
import io.github.tufein.duofrost.animations.LedAnimationType
import org.json.JSONArray
import org.json.JSONObject

/** Read only: unlike repository migration, opening a preview never changes stored JSON. */
internal object PresetPreviewSource {
    fun find(raw: String?, id: String?): LedPreset? {
        if (raw == null || raw.length > 2_000_000 || id == null || !PresetIdentity.isValid(id)) return null
        return runCatching {
            val presets = JSONArray(raw)
            var match: JSONObject? = null
            for (index in 0 until presets.length()) {
                val value = presets.optJSONObject(index) ?: continue
                if (value.opt("id") != id) continue
                if (match != null) return null // Ambiguous identity must not preview the wrong preset.
                match = value
            }
            match?.takeIf { LedAnimationType.fromStoredName(it.optString("animationType")) != null }
                ?.let { PresetCodec.decode(it) }
        }.getOrNull()
    }
}

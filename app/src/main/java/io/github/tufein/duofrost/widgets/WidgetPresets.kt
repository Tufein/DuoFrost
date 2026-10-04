package io.github.tufein.duofrost.widgets

import org.json.JSONArray

object WidgetPresets {
    fun names(raw: String?): List<String> {
        if (raw == null || raw.length > 1_048_576) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val names = linkedSetOf<String>()
        for (index in 0 until minOf(array.length(), 256)) {
            val name = array.optJSONObject(index)?.opt("name") as? String ?: continue
            if (name.isNotBlank() && name.length <= 128) names += name
        }
        return names.toList()
    }
}

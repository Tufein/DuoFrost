package io.github.tufein.duofrost.scenes

import android.content.Intent
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetCodec
import io.github.tufein.duofrost.animations.LedAnimationType
import org.json.JSONObject

/** A copy of the user's configuration, including unsaved editor values. */
object SceneBaseline {
    fun fromIntent(intent: Intent, id: String, name: String): LedPreset {
        val document = JSONObject().put("id", id).put("name", name)
        val extras = intent.extras
        extras?.keySet()?.forEach { key ->
            val value = extras.get(key)
            if (value is String || value is Int || value is Float || value is Boolean) {
                val storedKey = when (key) {
                    "animationColor" -> "color"
                    "animationRightColor" -> "rightColor"
                    else -> key
                }
                // Optional color sentinels represent absence, not actual RGB values.
                if (!(key.endsWith("ColorOverride") && value == Int.MIN_VALUE)) document.put(storedKey, value)
            }
        }
        if (!document.has("animationType")) document.put("animationType", LedAnimationType.AMBIENT.name)
        return PresetCodec.decode(document)
    }
}

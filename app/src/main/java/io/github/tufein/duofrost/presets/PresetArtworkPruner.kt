package io.github.tufein.duofrost

import android.content.Context
import android.content.SharedPreferences
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Remove old orphaned icons only when every stored preset's format is understood. */
object PresetArtworkPruner {
    // An import can stage artwork before it commits any presets. Quarantine new files until later.
    internal const val MIN_FILE_AGE_MILLIS = PresetUndoStore.TTL_MILLIS
    private val safeFileName = Regex("^[A-Za-z0-9._-]{1,96}$")
    private val knownFields = setOf("id", "name", "animationType", "performanceProfile", "color", "rightColor",
        "fadeEndColor", "fadeEndRightColor", "brightness", "speed", "smoothness", "sensitivity", "saturationBoost",
        "useCustomSampling", "useSingleColor", "breatheWhenCharging", "indicateChargingSpeed", "flashWhenReady",
        "batteryLowColorOverride", "batteryMidColorOverride", "batteryHighColorOverride", "cpuCoolColorOverride",
        "cpuWarmColorOverride", "cpuHotColorOverride", "isAppProfileDefault", "ragnarokAccepted", "icon",
        "customEmoji", "customImageFileName", "appIconPackageName", "ownerPackage")
    private val integerFields = setOf("color", "rightColor", "fadeEndColor", "fadeEndRightColor", "brightness",
        "batteryLowColorOverride", "batteryMidColorOverride", "batteryHighColorOverride", "cpuCoolColorOverride",
        "cpuWarmColorOverride", "cpuHotColorOverride")
    private val booleanFields = setOf("useCustomSampling", "useSingleColor", "breatheWhenCharging",
        "indicateChargingSpeed", "flashWhenReady", "isAppProfileDefault", "ragnarokAccepted")
    private val fractionFields = setOf("speed", "smoothness", "sensitivity", "saturationBoost")
    private val stringFields = setOf("customEmoji", "appIconPackageName", "ownerPackage")

    /** Holds the same lock as writers and undo while selecting and removing old candidates. */
    fun prune(context: Context, prefs: SharedPreferences): Int = synchronized(prefs) {
        val raw = prefs.all[PresetUndoStore.PREF_KEY_PRESETS] as? String ?: return@synchronized 0
        val referenced = referencedFileNames(raw) ?: return@synchronized 0
        val protected = referenced + PresetUndoStore(prefs).retainedArtworkFileNames().map { it.trim() }
        val now = System.currentTimeMillis()
        var removed = 0
        val directory = File(context.filesDir, "preset_icons")
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return@synchronized 0
        PresetImageStorage.listStoredIconFileNames(context).forEach { name ->
            if (!safeFileName.matches(name) || name in protected) return@forEach
            val file = File(directory, name)
            // Never follow a link outside the private icon directory, or race newly staged imports.
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
            val modified = file.lastModified()
            if (canonical.parentFile != canonicalDirectory || modified <= 0 || now < modified ||
                now - modified < MIN_FILE_AGE_MILLIS) return@forEach
            runCatching { PresetImageStorage.deleteIfExists(context, name) }
            if (!file.exists()) removed++
        }
        removed
    }

    /** Null means cleanup is unsafe. Unknown fields may hold references this version cannot see. */
    internal fun referencedFileNames(raw: String?): Set<String>? {
        if (raw == null || raw.length > PresetUndoStore.MAX_SNAPSHOT_BYTES ||
            raw.toByteArray(Charsets.UTF_8).size > PresetUndoStore.MAX_SNAPSHOT_BYTES) return null
        return runCatching {
            val array = JSONArray(raw)
            if (array.length() > PresetUndoStore.MAX_PRESETS) return null
            val ids = mutableSetOf<String>()
            buildSet {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: return null
                    if (obj.keys().asSequence().any { it !in knownFields } || !validFields(obj)) return null
                    val id = obj.opt("id") as? String ?: return null
                    if (!PresetIdentity.isValid(id) || !ids.add(id)) return null
                    if (obj.opt("name") !is String || (obj.opt("name") as String).isBlank()) return null
                    val type = obj.opt("animationType") as? String ?: return null
                    if (LedAnimationType.fromStoredName(type) == null) return null
                    if (obj.has("performanceProfile") && PerformanceProfile.entries.none { it.name == obj.opt("performanceProfile") }) return null
                    if (obj.has("icon") && PresetIcon.entries.none { it.name == obj.opt("icon") }) return null
                    if (obj.has("customImageFileName") && !obj.isNull("customImageFileName")) {
                        val name = (obj.opt("customImageFileName") as? String)?.trim() ?: return null
                        if (name.isNotEmpty()) {
                            if (!safeFileName.matches(name) || name == "." || name == "..") return null
                            add(name)
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun validFields(obj: JSONObject): Boolean {
        if (integerFields.any { obj.has(it) && !obj.isNull(it) && !validInt(obj.opt(it)) }) return false
        if (booleanFields.any { obj.has(it) && obj.opt(it) !is Boolean }) return false
        if (fractionFields.any { obj.has(it) && ((obj.opt(it) as? Number)?.toDouble()?.isFinite() != true) }) return false
        return stringFields.none { obj.has(it) && !obj.isNull(it) && obj.opt(it) !is String }
    }

    private fun validInt(value: Any?): Boolean = when (value) {
        is Int -> true
        is Long -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        else -> false
    }
}

package io.github.tufein.duofrost

import java.io.InputStream
import org.json.JSONObject

/** Selects an importer before a full-backup reader expands a community preset ZIP. */
internal object ArchiveSchemaProbe {
    enum class Type { BACKUP, PRESET }

    data class Result(val type: Type?, val errors: List<String>)

    fun read(
        input: InputStream,
        checkCancellation: () -> Unit = {}
    ): Result {
        val raw = PresetArchiveReader.readManifest(input, checkCancellation = checkCancellation)
            ?: return Result(null, listOf("Archive is missing manifest.json"))
        val manifest = runCatching { JSONObject(raw.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return Result(null, listOf("manifest.json is invalid JSON"))
        val type = when (manifest.optString("schema")) {
            "bifrost_full_backup" -> Type.BACKUP
            "bifrost_preset_bundle" -> Type.PRESET
            else -> return Result(null, listOf("Unknown backup or preset archive schema."))
        }
        return Result(type, emptyList())
    }
}

package io.github.tufein.duofrost

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import io.github.tufein.duofrost.animations.FadeTransitionAnimation
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.plugins.LivePolicy
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PresetArchiveTransfer {

    private const val ARCHIVE_SCHEMA = "bifrost_preset_bundle"
    // v2 adds the optional generic `livePolicies` block (effect name → LivePolicy).
    // Older DuoFrost reads a v2 bundle fine — it imports the presets and ignores
    // the block it doesn't know; newer DuoFrost reads a v1 bundle fine — no block.
    private const val ARCHIVE_VERSION = 2
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val ICONS_DIR_PREFIX = "icons/"

    data class ExportResult(
        val presetCount: Int,
        val iconCount: Int,
        val warnings: List<String>
    )

    data class ImportResult(
        val presets: List<LedPreset>,
        val mappings: Map<String, String>,
        val warnings: List<String>,
        val errors: List<String>,
        // Generic plugin live-feed policies (caller package → policy). Empty for
        // ordinary user preset bundles; populated by plugins that declare them.
        val livePolicies: Map<String, LivePolicy> = emptyMap()
    )

    internal data class ValidatedArchive(
        val manifest: JSONObject?,
        val entries: Map<String, ByteArray>,
        val errors: List<String>
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        presets: List<LedPreset>,
        mappings: Map<String, String>,
        cancelSignal: CancellationSignal? = null
    ): ExportResult {
        val warnings = mutableListOf<String>()
        var exportedIconCount = 0

        val manifestPresets = JSONArray()
        presets.forEach { preset ->
            val presetJson = JSONObject()
            presetJson.put("id", preset.id)
            presetJson.put("name", preset.name)
            presetJson.put("animationType", preset.animationType.name)
            presetJson.put("performanceProfile", preset.performanceProfile.name)
            presetJson.put("color", preset.color)
            presetJson.put("rightColor", preset.rightColor)
            presetJson.put("fadeEndColor", preset.fadeEndColor)
            presetJson.put("fadeEndRightColor", preset.fadeEndRightColor)
            presetJson.put("brightness", preset.brightness)
            presetJson.put("speed", preset.speed.toDouble())
            presetJson.put("smoothness", preset.smoothness.toDouble())
            presetJson.put("sensitivity", preset.sensitivity.toDouble())
            presetJson.put("saturationBoost", preset.saturationBoost.toDouble())
            presetJson.put("useCustomSampling", preset.useCustomSampling)
            presetJson.put("useSingleColor", preset.useSingleColor)
            presetJson.put("breatheWhenCharging", preset.breatheWhenCharging)
            presetJson.put("indicateChargingSpeed", preset.indicateChargingSpeed)
            presetJson.put("flashWhenReady", preset.flashWhenReady)
            preset.batteryLowColorOverride?.let { presetJson.put("batteryLowColorOverride", it) }
            preset.batteryMidColorOverride?.let { presetJson.put("batteryMidColorOverride", it) }
            preset.batteryHighColorOverride?.let { presetJson.put("batteryHighColorOverride", it) }
            preset.cpuCoolColorOverride?.let { presetJson.put("cpuCoolColorOverride", it) }
            preset.cpuWarmColorOverride?.let { presetJson.put("cpuWarmColorOverride", it) }
            preset.cpuHotColorOverride?.let { presetJson.put("cpuHotColorOverride", it) }
            presetJson.put("isAppProfileDefault", preset.isAppProfileDefault)
            presetJson.put("ragnarokAccepted", preset.ragnarokAccepted)
            presetJson.put("icon", preset.icon.name)
            preset.customEmoji?.let { presetJson.put("customEmoji", it) }
            preset.customImageFileName?.let { presetJson.put("customImageFileName", it) }
            preset.appIconPackageName?.let { presetJson.put("appIconPackageName", it) }
            manifestPresets.put(presetJson)
        }

        val mappingsJson = JSONObject()
        mappings.forEach { (packageName, presetName) ->
            mappingsJson.put(packageName, presetName)
        }

        val manifest = JSONObject().apply {
            put("schema", ARCHIVE_SCHEMA)
            put("version", ARCHIVE_VERSION)
            put("presets", manifestPresets)
            put("appProfileMappings", mappingsJson)
        }

        val seenIconNames = linkedSetOf<String>()
        // Check for cancellation before starting heavy IO
        cancelSignal?.throwIfCanceled()
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Unable to write selected preset file.")
        output.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                cancelSignal?.throwIfCanceled()
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                presets.forEach { preset ->
                    cancelSignal?.throwIfCanceled()
                    val iconName = preset.customImageFileName?.trim().orEmpty()
                    if (iconName.isEmpty() || !seenIconNames.add(iconName)) return@forEach

                    val input = PresetImageStorage.openIconInputStream(context, iconName)
                    if (input == null) {
                        warnings += "Image not found for preset icon '$iconName'; skipping."
                        return@forEach
                    }

                    input.use { iconStream ->
                        cancelSignal?.throwIfCanceled()
                        zip.putNextEntry(ZipEntry("$ICONS_DIR_PREFIX$iconName"))
                        iconStream.copyTo(zip)
                        zip.closeEntry()
                        exportedIconCount++
                    }
                }
            }
        }

        return ExportResult(
            presetCount = presets.size,
            iconCount = exportedIconCount,
            warnings = warnings
        )
    }

    fun importFromUri(context: Context, uri: Uri, cancelSignal: CancellationSignal? = null): ImportResult {
        val warnings = mutableListOf<String>()
        // Check cancellation before reading
        cancelSignal?.throwIfCanceled()
        val archive = context.contentResolver.openInputStream(uri)?.use { input ->
            readValidatedArchive(input) { cancelSignal?.throwIfCanceled() }
        } ?: return ImportResult(
            presets = emptyList(),
            mappings = emptyMap(),
            warnings = emptyList(),
            errors = listOf("Unable to read selected file.")
        )
        val manifest = archive.manifest ?: run {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = archive.errors
            )
        }
        val zipEntries = archive.entries

        val presetArray = manifest.optJSONArray("presets") ?: JSONArray()
        val importedPresets = mutableListOf<LedPreset>()
        val importedIconNames = mutableMapOf<String, String?>()

        for (index in 0 until presetArray.length()) {
            cancelSignal?.throwIfCanceled()
            val obj = presetArray.optJSONObject(index) ?: continue
            importedPresets += parsePreset(context, obj, index, zipEntries, importedIconNames, warnings, cancelSignal)
        }

        val mappings = parseMappings(context, manifest.optJSONObject("appProfileMappings"), warnings, cancelSignal)
        val livePolicies = parseLivePolicies(manifest.optJSONObject("livePolicies"), warnings)

        return ImportResult(
            presets = PresetIdentity.normalizeImported(importedPresets),
            mappings = mappings,
            warnings = warnings,
            errors = emptyList(),
            livePolicies = livePolicies
        )
    }

    /** Validation is complete before the Android importer can write any artwork. */
    internal fun readValidatedArchive(
        input: InputStream,
        checkCancellation: () -> Unit = {}
    ): ValidatedArchive {
        val entries = PresetArchiveReader.read(input, checkCancellation = checkCancellation)
        val manifestRaw = entries[MANIFEST_ENTRY_NAME]
            ?: return ValidatedArchive(null, emptyMap(), listOf("Archive is missing manifest.json"))
        val manifest = runCatching { JSONObject(manifestRaw.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return ValidatedArchive(null, emptyMap(), listOf("manifest.json is invalid JSON"))
        val errors = mutableListOf<String>()
        if (manifest.optString("schema") != ARCHIVE_SCHEMA) {
            errors += "Unknown preset bundle schema."
        }
        val version = manifest.opt("version") as? Int
        if (version == null || version !in 1..ARCHIVE_VERSION) {
            errors += "Unsupported preset bundle version: ${manifest.opt("version") ?: "missing"}"
        }
        return if (errors.isEmpty()) {
            ValidatedArchive(manifest, entries, emptyList())
        } else {
            ValidatedArchive(null, emptyMap(), errors)
        }
    }

    internal fun cachedImportedIcon(
        sourceName: String,
        importedNames: MutableMap<String, String?>,
        importIcon: () -> String?
    ): String? {
        if (importedNames.containsKey(sourceName)) return importedNames[sourceName]
        return importIcon().also { importedNames[sourceName] = it }
    }

    /**
     * Parse the generic `livePolicies` block: a JSON object of effect name →
     * policy object. Unknown/garbage entries are skipped with a warning. Generic
     * — no per-app knowledge; any plugin can declare policies for the effects it owns.
     */
    private fun parseLivePolicies(
        obj: JSONObject?,
        warnings: MutableList<String>
    ): Map<String, LivePolicy> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, LivePolicy>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val effect = keys.next()
            val policyObj = obj.optJSONObject(effect)
            if (policyObj == null) {
                warnings += "livePolicies: entry for '$effect' is not an object; skipping."
                continue
            }
            out[effect] = LivePolicy.fromJson(policyObj)
        }
        return out
    }

    private fun parsePreset(
        context: Context,
        obj: JSONObject,
        index: Int,
        zipEntries: Map<String, ByteArray>,
        importedIconNames: MutableMap<String, String?>,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): LedPreset {
        cancelSignal?.throwIfCanceled()
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Imported Preset ${index + 1}"

        val animationTypeName = obj.optString("animationType", LedAnimationType.STATIC.name)
        val animationType = runCatching { LedAnimationType.fromStoredName(animationTypeName)!! }
            .onFailure { warnings += "Preset '$name': unknown animation '$animationTypeName', using STATIC." }
            .getOrDefault(LedAnimationType.STATIC)

        val profileName = obj.optString("performanceProfile", PerformanceProfile.HIGH.name)
        val profile = runCatching { PerformanceProfile.valueOf(profileName) }
            .onFailure { warnings += "Preset '$name': unknown profile '$profileName', using HIGH." }
            .getOrDefault(PerformanceProfile.HIGH)

        val iconName = obj.optString("icon", PresetIcon.defaultFor(animationType).name)
        val icon = PresetIcon.fromStoredName(iconName)

        val customEmoji = obj.optString("customEmoji").takeIf { it.isNotBlank() }

        val importedIconName = obj.optString("customImageFileName").takeIf { it.isNotBlank() }
        val customImageFileName = if (importedIconName != null) {
            cancelSignal?.throwIfCanceled()
            cachedImportedIcon(importedIconName, importedIconNames) {
                val archiveEntryName = "$ICONS_DIR_PREFIX$importedIconName"
                val iconBytes = zipEntries[archiveEntryName]
                if (iconBytes == null) {
                    warnings += "Preset '$name': missing icon file '$importedIconName'."
                    null
                } else {
                    PresetImageStorage.importIconFromBytes(context, importedIconName, iconBytes)
                        ?: run {
                            warnings += "Preset '$name': could not import icon '$importedIconName'."
                            null
                        }
                }
            }
        } else {
            null
        }

        val requestedAppIconPackage = obj.optString("appIconPackageName").takeIf { it.isNotBlank() }
        val appIconPackageName = requestedAppIconPackage?.takeIf {
            cancelSignal?.throwIfCanceled(); isPackageInstalled(context.packageManager, it)
        } ?: run {
            if (!requestedAppIconPackage.isNullOrBlank()) {
                warnings += "Preset '$name': assigned app icon package '$requestedAppIconPackage' is not installed, skipping."
            }
            null
        }

        val color = obj.optInt("color", -1)

        return LedPreset(
            name = name,
            animationType = animationType,
            performanceProfile = profile,
            color = color,
            rightColor = obj.optInt("rightColor", color),
            fadeEndColor = obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR),
            fadeEndRightColor = obj.optInt("fadeEndRightColor", obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)),
            brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
            speed = obj.optDouble("speed", 0.5).toFloat().coerceIn(0f, 1f),
            smoothness = obj.optDouble("smoothness", 0.5).toFloat().coerceIn(0f, 1f),
            sensitivity = obj.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f),
            saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat().coerceIn(0f, 1f),
            useCustomSampling = obj.optBoolean("useCustomSampling", false),
            useSingleColor = obj.optBoolean("useSingleColor", false),
            breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
            indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
            flashWhenReady = obj.optBoolean("flashWhenReady", false),
            batteryLowColorOverride = obj.optInt("batteryLowColorOverride").takeIf { obj.has("batteryLowColorOverride") },
            batteryMidColorOverride = obj.optInt("batteryMidColorOverride").takeIf { obj.has("batteryMidColorOverride") },
            batteryHighColorOverride = obj.optInt("batteryHighColorOverride").takeIf { obj.has("batteryHighColorOverride") },
            cpuCoolColorOverride = obj.optInt("cpuCoolColorOverride").takeIf { obj.has("cpuCoolColorOverride") },
            cpuWarmColorOverride = obj.optInt("cpuWarmColorOverride").takeIf { obj.has("cpuWarmColorOverride") },
            cpuHotColorOverride = obj.optInt("cpuHotColorOverride").takeIf { obj.has("cpuHotColorOverride") },
            isAppProfileDefault = obj.optBoolean("isAppProfileDefault", false),
            ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
            icon = icon,
            customEmoji = customEmoji,
            customImageFileName = customImageFileName,
            appIconPackageName = appIconPackageName,
            id = (obj.opt("id") as? String)?.takeIf(PresetIdentity::isValid) ?: PresetIdentity.newId()
        )
    }

    private fun parseMappings(
        context: Context,
        mappingsObj: JSONObject?,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): Map<String, String> {
        if (mappingsObj == null) return emptyMap()

        val mappings = linkedMapOf<String, String>()
        val iterator = mappingsObj.keys()
        while (iterator.hasNext()) {
            cancelSignal?.throwIfCanceled()
            val packageName = iterator.next()
            val presetName = mappingsObj.optString(packageName).takeIf { it.isNotBlank() }
            if (presetName == null) continue

            if (!isPackageInstalled(context.packageManager, packageName)) {
                warnings += "Mapping ignored: package '$packageName' is not installed."
                continue
            }
            mappings[packageName] = presetName
        }
        return mappings
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}

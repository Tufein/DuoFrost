package io.github.tufein.duofrost

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresetArtworkPrunerTest {
    @Test fun recognizedLibraryKeepsAllSharedAndTrimmedImageReferences() {
        val first = preset("a", " art.png ")
        val second = preset("b", "art.png")
        val third = preset("c", "second.webp")
        assertEquals(setOf("art.png", "second.webp"), PresetArtworkPruner.referencedFileNames(JSONArray(listOf(first, second, third)).toString()))
    }

    @Test fun knownCompleteCodecFormatIsRecognizedWithoutChangingLightingOrArtworkData() {
        val original = preset("a", "art.png").put("ownerPackage", "plugin:retro").put("customEmoji", "🎮")
        val encoded = PresetCodec.encode(PresetCodec.decode(original))
        val raw = JSONArray(listOf(encoded)).toString()
        assertEquals(setOf("art.png"), PresetArtworkPruner.referencedFileNames(raw))
        assertEquals(raw, JSONArray(listOf(encoded)).toString())
    }

    @Test fun intentionalEmptyArrayAndPresetsWithoutImagesHaveNoReferences() {
        assertEquals(emptySet<String>(), PresetArtworkPruner.referencedFileNames("[]"))
        val noImage = preset("a", null)
        val blankImage = preset("b", "  ")
        assertEquals(emptySet<String>(), PresetArtworkPruner.referencedFileNames(JSONArray(listOf(noImage, blankImage)).toString()))
    }

    @Test fun missingMalformedOrUnrecognizedStructuresNeverAllowPruning() {
        for (raw in listOf(null, "invalid", "{}", "null", "[42]", "[null]", "[\"unknown\"]", "[{}]")) {
            assertNull("raw=$raw", PresetArtworkPruner.referencedFileNames(raw))
        }
        assertNull(PresetArtworkPruner.referencedFileNames("""[{"name":"Legacy without identity","animationType":"STATIC"}]"""))
    }

    @Test fun futureFieldsEnumsAndDuplicateIdentityProtectTheEntireLibrary() {
        val mutations = listOf(
            preset("a", "art.png").put("futureArtwork", "other.png"),
            preset("a", "art.png").put("animationType", "FUTURE_EFFECT"),
            preset("a", "art.png").put("performanceProfile", "FUTURE_PROFILE"),
            preset("a", "art.png").put("icon", "FUTURE_ICON"),
            preset("bad id", "art.png")
        )
        mutations.forEach { assertNull(PresetArtworkPruner.referencedFileNames(JSONArray(listOf(it)).toString())) }
        assertNull(PresetArtworkPruner.referencedFileNames(JSONArray(listOf(preset("a", "first.png"), preset("a", "second.png"))).toString()))
    }

    @Test fun malformedFieldsAndUnsafeArtworkPathsProtectAllExistingFiles() {
        val changes = listOf("customImageFileName" to 42, "customImageFileName" to "../art.png",
            "customImageFileName" to "sub/art.png", "customImageFileName" to "..", "brightness" to "200",
            "speed" to "0.5", "useSingleColor" to "true", "ownerPackage" to 42)
        changes.forEach { (key, value) ->
            val invalid = preset("a", "art.png").put(key, value)
            assertNull("$key=$value", PresetArtworkPruner.referencedFileNames(JSONArray(listOf(invalid)).toString()))
        }
    }

    @Test fun resourceBoundsFailClosedBeforeCleanup() {
        val presets = (0..PresetUndoStore.MAX_PRESETS).map { preset("id-$it", "art.png") }
        assertNull(PresetArtworkPruner.referencedFileNames(JSONArray(presets).toString()))
        val oversized = preset("a", "art.png").put("customEmoji", "é".repeat(PresetUndoStore.MAX_SNAPSHOT_BYTES / 2))
        assertNull(PresetArtworkPruner.referencedFileNames(JSONArray(listOf(oversized)).toString()))
        assertEquals(PresetUndoStore.TTL_MILLIS, PresetArtworkPruner.MIN_FILE_AGE_MILLIS)
    }

    private fun preset(id: String, image: String?): JSONObject = JSONObject().put("id", id).put("name", "Preset $id")
        .put("animationType", "STATIC").apply { image?.let { put("customImageFileName", it) } }
}

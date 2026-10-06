package io.github.tufein.duofrost.ui

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EditorDraftCodecTest {
    private val lighting = LedPreset(
        name = "Unfinished café look",
        animationType = LedAnimationType.AMBIAURORA,
        performanceProfile = PerformanceProfile.MEDIUM,
        color = -16_711_681,
        rightColor = -65_536,
        fadeEndColor = Int.MIN_VALUE,
        fadeEndRightColor = Int.MAX_VALUE,
        brightness = 153,
        speed = 0.1f,
        smoothness = 0.2f,
        sensitivity = 0.3f,
        saturationBoost = 0.4f,
        useCustomSampling = true,
        useSingleColor = true,
        breatheWhenCharging = true,
        indicateChargingSpeed = true,
        flashWhenReady = true,
        batteryLowColorOverride = -65_536,
        batteryMidColorOverride = 0,
        batteryHighColorOverride = -16_711_936,
        cpuCoolColorOverride = -16_776_961,
        cpuWarmColorOverride = -256,
        cpuHotColorOverride = -1
    )

    private fun changed(key: String, value: Any): String =
        JSONObject(EditorDraftCodec.encode(lighting)).put(key, value).toString()

    @Test fun rotationDraftRoundTripsEveryUnsavedLightingField() {
        assertEquals(lighting, EditorDraftCodec.decode(EditorDraftCodec.encode(lighting)))
    }

    @Test fun unsetPaletteOverridesRemainNullAndNeverBecomeBlack() {
        val withoutPalette = lighting.copy(
            batteryLowColorOverride = null,
            batteryMidColorOverride = null,
            batteryHighColorOverride = null,
            cpuCoolColorOverride = null,
            cpuWarmColorOverride = null,
            cpuHotColorOverride = null
        )
        assertEquals(withoutPalette, EditorDraftCodec.decode(EditorDraftCodec.encode(withoutPalette)))
    }

    @Test fun draftExcludesPresetMetadataAndPermissionRelatedAcceptance() {
        val withMetadata = lighting.copy(
            isAppProfileDefault = true,
            ragnarokAccepted = true,
            customEmoji = "🌈",
            customImageFileName = "private.png",
            appIconPackageName = "example.game",
            ownerPackage = "example.plugin"
        )
        val encoded = EditorDraftCodec.encode(withMetadata)
        val root = JSONObject(encoded)
        listOf("isAppProfileDefault", "ragnarokAccepted", "icon", "customEmoji", "customImageFileName",
            "appIconPackageName", "ownerPackage", "running", "muted", "captureGrant", "projectionToken")
            .forEach { assertFalse(it, root.has(it)) }
        assertEquals(lighting, EditorDraftCodec.decode(encoded))
    }

    @Test fun unknownMetadataCannotBeRestoredIntoTheLightingDraft() {
        val raw = JSONObject(EditorDraftCodec.encode(lighting))
            .put("isAppProfileDefault", true)
            .put("ragnarokAccepted", true)
            .put("customImageFileName", "injected.png")
            .put("captureGrant", JSONObject().put("token", "not a grant"))
            .toString()
        assertEquals(lighting, EditorDraftCodec.decode(raw))
    }

    @Test fun emptyEditorNamesAndSupportedBoundariesRoundTrip() {
        val draft = lighting.copy(name = "", brightness = 0, speed = 0f, smoothness = 1f,
            sensitivity = 0f, saturationBoost = 1f)
        assertEquals(draft, EditorDraftCodec.decode(EditorDraftCodec.encode(draft)))
        val bright = draft.copy(name = "x".repeat(128), brightness = 255)
        assertEquals(bright, EditorDraftCodec.decode(EditorDraftCodec.encode(bright)))
    }

    @Test fun invalidEnumsBrightnessAndNumbersAreRejected() {
        assertNull(EditorDraftCodec.decode(changed("animationType", "NOT_AN_EFFECT")))
        assertNull(EditorDraftCodec.decode(changed("performanceProfile", "TURBO")))
        listOf(-1, 256, 1.5, "128").forEach {
            assertNull(EditorDraftCodec.decode(changed("brightness", it)))
        }
        listOf("speed", "smoothness", "sensitivity", "saturationBoost").forEach { key ->
            listOf(-0.1, 1.1, "NaN", "Infinity", "0.5").forEach { value ->
                assertNull("$key=$value", EditorDraftCodec.decode(changed(key, value)))
            }
            val nonFinite = EditorDraftCodec.encode(lighting).replace(
                "\"$key\":" + JSONObject(EditorDraftCodec.encode(lighting)).get(key),
                "\"$key\":1e999"
            )
            assertNull(EditorDraftCodec.decode(nonFinite))
        }
    }

    @Test fun incompleteWrongTypedAndWrongSchemaDraftsAreRejected() {
        val missing = JSONObject(EditorDraftCodec.encode(lighting)).apply { remove("cpuCoolColorOverride") }
        assertNull(EditorDraftCodec.decode(missing.toString()))
        assertNull(EditorDraftCodec.decode(changed("useSingleColor", "true")))
        assertNull(EditorDraftCodec.decode(changed("color", 2_147_483_648L)))
        assertNull(EditorDraftCodec.decode(changed("batteryLowColorOverride", 2.5)))
        assertNull(EditorDraftCodec.decode(changed("name", 7)))
        assertNull(EditorDraftCodec.decode(changed("schema", "another_draft")))
        assertNull(EditorDraftCodec.decode(changed("version", 2)))
    }

    @Test fun malformedTrailingAndOversizedInputIsIgnored() {
        listOf(null, "", " ", "{", "[]", "{}", EditorDraftCodec.encode(lighting) + " junk")
            .forEach { assertNull(EditorDraftCodec.decode(it)) }
        assertNull(EditorDraftCodec.decode(changed("name", "x".repeat(129))))
        assertNull(EditorDraftCodec.decode(changed("padding", "x".repeat(16 * 1024))))
        // Fits the character bound, but exceeds the UTF-8 byte bound.
        assertNull(EditorDraftCodec.decode(changed("padding", "界".repeat(6_000))))
    }
}

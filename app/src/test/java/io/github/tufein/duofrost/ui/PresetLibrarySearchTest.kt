package io.github.tufein.duofrost.ui

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PresetLibrarySearchTest {
    private fun preset(name: String, effect: LedAnimationType = LedAnimationType.STATIC) =
        LedPreset(
            name = name,
            animationType = effect,
            performanceProfile = PerformanceProfile.HIGH,
            color = -1,
            brightness = 128,
            speed = 0.5f,
            smoothness = 0.5f
        )

    private val presets = listOf(
        preset("Desk"),
        preset("Café glow", LedAnimationType.AUDIO_REACTIVE),
        preset("Night", LedAnimationType.AMBIENT),
        preset("Café rainbow", LedAnimationType.RAINBOW)
    )

    @Test fun blankQueryKeepsEveryOriginalIndex() {
        assertEquals(listOf(0, 1, 2, 3), PresetLibrarySearch.matchingIndices(presets, "  \t\n"))
    }

    @Test fun namesMatchAcrossCaseAndAccentsWithoutRenumberingResults() {
        assertEquals(listOf(1, 3), PresetLibrarySearch.matchingIndices(presets, "  CAFE  "))
        assertEquals(listOf(1), PresetLibrarySearch.matchingIndices(presets, "café GLOW"))
    }

    @Test fun effectNamesMatchUserFacingSpacesAndEnumUnderscores() {
        assertEquals(listOf(1), PresetLibrarySearch.matchingIndices(presets, "audio reactive"))
        assertEquals(listOf(1), PresetLibrarySearch.matchingIndices(presets, "AUDIO_REACTIVE"))
        assertEquals(listOf(1), PresetLibrarySearch.matchingIndices(presets, "audio   reactive"))
        assertEquals(listOf(2), PresetLibrarySearch.matchingIndices(presets, "AMBIENT"))
    }

    @Test fun absentMatchesAndEmptyLibrariesReturnNoIndices() {
        assertEquals(emptyList<Int>(), PresetLibrarySearch.matchingIndices(presets, "missing"))
        assertEquals(emptyList<Int>(), PresetLibrarySearch.matchingIndices(emptyList(), "glow"))
        assertEquals(emptyList<Int>(), PresetLibrarySearch.matchingIndices(emptyList(), ""))
    }

    @Test fun searchingDoesNotMutateOrReorderTheLibrary() {
        val mutable = presets.toMutableList()
        val original = mutable.toList()
        PresetLibrarySearch.matchingIndices(mutable, "rainbow")
        assertEquals(original, mutable)
        original.indices.forEach { assertSame(original[it], mutable[it]) }
    }

    @Test fun matchingDoesNotDependOnDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf(0), PresetLibrarySearch.matchingIndices(listOf(preset("ICELIGHT")), "icelight"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

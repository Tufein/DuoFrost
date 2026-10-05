package io.github.tufein.duofrost

import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.junit.Assert.*
import org.junit.Test

class PresetLightingChangesTest {
    private val saved = LedPreset(
        name = "Palette",
        animationType = LedAnimationType.STATIC,
        performanceProfile = PerformanceProfile.HIGH,
        color = -1,
        brightness = 128,
        speed = 0.5f,
        smoothness = 0.5f
    )

    @Test fun everyBatteryAndCpuPaletteChangeIsDetected() {
        val changes = listOf(
            saved.copy(batteryLowColorOverride = 0x123456),
            saved.copy(batteryMidColorOverride = 0x123456),
            saved.copy(batteryHighColorOverride = 0x123456),
            saved.copy(cpuCoolColorOverride = 0x123456),
            saved.copy(cpuWarmColorOverride = 0x123456),
            saved.copy(cpuHotColorOverride = 0x123456)
        )
        changes.forEach { changed ->
            assertTrue(PresetController.hasLightingChanges(changed, saved))
            assertTrue(PresetController.hasLightingChanges(saved, changed))
        }
    }

    @Test fun savedPaletteAndPresentationOnlyChangesDoNotPromptToSave() {
        assertFalse(PresetController.hasLightingChanges(saved, saved.copy()))
        assertFalse(PresetController.hasLightingChanges(saved.copy(
            name = "Renamed",
            icon = PresetIcon.DISPLAY,
            customEmoji = "🌈",
            isAppProfileDefault = true
        ), saved))
    }

    @Test fun ordinaryLightingChangesStillRequireSaving() {
        assertTrue(PresetController.hasLightingChanges(saved.copy(brightness = 64), saved))
        assertTrue(PresetController.hasLightingChanges(saved.copy(rightColor = 0), saved))
    }

    @Test fun deletingOrReplacingOnePresetPreservesSharedArtwork() {
        val first = saved.copy(customImageFileName = "shared.png")
        val second = saved.copy(name = "Other", customImageFileName = "shared.png")
        assertFalse(PresetController.canDeleteArtwork("shared.png", listOf(first, second), excludedIndex = 0))
        assertFalse(PresetController.canDeleteArtwork("shared.png", listOf(first, second), excludedIndex = 1))
        assertTrue(PresetController.canDeleteArtwork("shared.png", listOf(first), excludedIndex = 0))
        assertFalse(PresetController.canDeleteArtwork(null, listOf(saved), excludedIndex = 0))
    }
}

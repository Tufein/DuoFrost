package io.github.tufein.duofrost.services

import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.tufein.duofrost.services.AppProfileSelection.Source

class AppProfileSelectionTest {
    private val mappings = mapOf("com.example.game" to "Per-game")

    @Test fun explicitAppMappingWinsOverGameScene() {
        assertEquals(
            AppProfileSelection.Result("Per-game", Source.APP_MAPPING),
            AppProfileSelection.resolve(
                currentPackage = "com.example.game",
                selfPackage = "io.github.tufein.duofrost",
                homePackages = emptySet(),
                mappings = mappings,
                gameSceneEnabled = true,
                gameScenePresetName = "All games",
                isGamePackage = true,
                fallbackPresetName = "Default"
            )
        )
    }

    @Test fun gameSceneCoversUnmappedGames() {
        assertEquals(
            AppProfileSelection.Result("All games", Source.GAME_SCENE),
            AppProfileSelection.resolve(
                currentPackage = "com.example.othergame",
                selfPackage = "io.github.tufein.duofrost",
                homePackages = emptySet(),
                mappings = mappings,
                gameSceneEnabled = true,
                gameScenePresetName = "All games",
                isGamePackage = true,
                fallbackPresetName = "Default"
            )
        )
    }

    @Test fun disabledOrNonGameAppsUseTheDefault() {
        val disabled = AppProfileSelection.resolve(
            currentPackage = "com.example.othergame",
            selfPackage = "io.github.tufein.duofrost",
            homePackages = emptySet(),
            mappings = emptyMap(),
            gameSceneEnabled = false,
            gameScenePresetName = "All games",
            isGamePackage = true,
            fallbackPresetName = "Default"
        )
        val nonGame = AppProfileSelection.resolve(
            currentPackage = "com.example.video",
            selfPackage = "io.github.tufein.duofrost",
            homePackages = emptySet(),
            mappings = emptyMap(),
            gameSceneEnabled = true,
            gameScenePresetName = "All games",
            isGamePackage = false,
            fallbackPresetName = "Default"
        )
        assertEquals(AppProfileSelection.Result("Default", Source.FALLBACK), disabled)
        assertEquals(AppProfileSelection.Result("Default", Source.FALLBACK), nonGame)
    }

    @Test fun homeAndDuoFrostNeverUseTheGameScene() {
        val home = AppProfileSelection.resolve(
            currentPackage = "com.example.launcher",
            selfPackage = "io.github.tufein.duofrost",
            homePackages = setOf("com.example.launcher"),
            mappings = emptyMap(),
            gameSceneEnabled = true,
            gameScenePresetName = "All games",
            isGamePackage = true,
            fallbackPresetName = "Default"
        )
        val self = AppProfileSelection.resolve(
            currentPackage = "io.github.tufein.duofrost",
            selfPackage = "io.github.tufein.duofrost",
            homePackages = emptySet(),
            mappings = emptyMap(),
            gameSceneEnabled = true,
            gameScenePresetName = "All games",
            isGamePackage = true,
            fallbackPresetName = "Default"
        )
        assertEquals(AppProfileSelection.Result("Default", Source.FALLBACK), home)
        assertEquals(AppProfileSelection.Result("Default", Source.FALLBACK), self)
    }

    @Test fun transientForegroundFailureDoesNotSelectAStalePreset() {
        assertEquals(
            AppProfileSelection.Result(null, Source.NONE),
            AppProfileSelection.resolve(
                currentPackage = null,
                selfPackage = "io.github.tufein.duofrost",
                homePackages = emptySet(),
                mappings = emptyMap(),
                gameSceneEnabled = true,
                gameScenePresetName = "All games",
                isGamePackage = true,
                fallbackPresetName = "Default"
            )
        )
    }
}

package io.github.tufein.duofrost.services

/**
 * Chooses a preset for a foreground package without touching Android state.
 * An explicit app mapping is always more specific than the broad game scene.
 */
object AppProfileSelection {
    enum class Source {
        NONE,
        FALLBACK,
        GAME_SCENE,
        APP_MAPPING
    }

    data class Result(
        val presetName: String?,
        val source: Source
    )

    fun resolve(
        currentPackage: String?,
        selfPackage: String,
        homePackages: Set<String>,
        mappings: Map<String, String>,
        gameSceneEnabled: Boolean,
        gameScenePresetName: String?,
        isGamePackage: Boolean,
        fallbackPresetName: String?
    ): Result {
        val fallback = fallbackPresetName.cleanName()
        val current = currentPackage.cleanName() ?: return Result(null, Source.NONE)

        if (current == selfPackage || current in homePackages) {
            return Result(fallback, Source.FALLBACK)
        }

        mappings[current].cleanName()?.let { mapped ->
            return Result(mapped, Source.APP_MAPPING)
        }

        gameScenePresetName.cleanName()?.let { gamePreset ->
            if (gameSceneEnabled && isGamePackage) {
                return Result(gamePreset, Source.GAME_SCENE)
            }
        }

        return Result(fallback, Source.FALLBACK)
    }

    private fun String?.cleanName(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

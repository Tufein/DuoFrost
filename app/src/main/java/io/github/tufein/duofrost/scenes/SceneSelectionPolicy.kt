package io.github.tufein.duofrost.scenes

import io.github.tufein.duofrost.services.AppProfileSelection

/** Keeps legacy app-profile specificity while adding general scene rules. */
object SceneSelectionPolicy {
    enum class Source { TEMPORARY, APP_PROFILE, RULE, GAME_SCENE, DEFAULT, MANUAL }
    data class Choice(val presetId: String?, val source: Source)

    fun choose(
        temporaryPresetId: String?,
        legacySource: AppProfileSelection.Source?,
        legacyPresetId: String?,
        evaluation: SceneEvaluation,
        baselinePresetId: String?
    ): Choice = when {
        temporaryPresetId != null -> Choice(temporaryPresetId, Source.TEMPORARY)
        legacySource == AppProfileSelection.Source.APP_MAPPING -> Choice(legacyPresetId, Source.APP_PROFILE)
        evaluation.presetId != null -> Choice(evaluation.presetId, Source.RULE)
        legacySource == AppProfileSelection.Source.GAME_SCENE -> Choice(legacyPresetId, Source.GAME_SCENE)
        legacySource == AppProfileSelection.Source.FALLBACK -> Choice(legacyPresetId, Source.DEFAULT)
        else -> Choice(baselinePresetId, Source.MANUAL)
    }

    fun brightnessLimit(choice: Choice, evaluation: SceneEvaluation): Int? =
        if (choice.source == Source.RULE) evaluation.brightnessLimitPercent else evaluation.modifierLimitPercent
}

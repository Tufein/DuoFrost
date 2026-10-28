package io.github.tufein.duofrost.scenes

import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.services.AppProfileSelection

/** The real selection policies, without Android state, commands or mutations. */
object SceneSimulator {
    data class Result(val preset: LedPreset?, val source: SceneSelectionPolicy.Source,
        val limit: Int?, val matching: List<String>, val ruleName: String?)
    fun evaluate(presets: List<LedPreset>, rules: List<SceneRule>, groups: List<AppGroup>,
        context: SceneContext, mappings: Map<String, String>, appProfiles: Boolean,
        gameScene: Boolean, gamePreset: String?, baselineName: String?, temporaryId: String?,
        selfPackage: String): Result {
        val byId = presets.associateBy { it.id }
        val byName = presets.groupBy { it.name }.mapValues { it.value.first() }
        val evaluation = SceneEvaluator.evaluate(rules, groups, context, byId.keys)
        val legacy = if (appProfiles) AppProfileSelection.resolve(context.packageName, selfPackage,
            if (context.isHome) setOfNotNull(context.packageName) else emptySet(), mappings,
            gameScene, gamePreset, context.isGame, presets.firstOrNull { it.isAppProfileDefault }?.name) else null
        val legacyId = legacy?.presetName?.let { byName[it]?.id }
        val choice = SceneSelectionPolicy.choose(temporaryId?.takeIf { it in byId },
            legacy?.source?.takeUnless { it == AppProfileSelection.Source.GAME_SCENE && legacyId == null },
            legacyId, evaluation, byName[baselineName]?.id)
        return Result(byId[choice.presetId], choice.source,
            SceneSelectionPolicy.brightnessLimit(choice, evaluation), evaluation.matchedRuleNames,
            evaluation.presetRule?.name)
    }
}

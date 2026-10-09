package io.github.tufein.duofrost.scenes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.PresetController
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.services.AppProfileSelection
import java.time.LocalDateTime

/** Runs only inside the existing lighting service; editing scenes never starts it. */
class SceneRuntimeController(
    prefs: SharedPreferences,
    private val appProfiles: AppProfileManager
) {
    data class Decision(
        val preset: LedPreset?,
        val presetName: String?,
        val presetChanged: Boolean,
        val brightnessLimitPercent: Int?,
        val reason: String
    )

    private val store = SceneStore(prefs)
    private val repository = PresetRepository(prefs)
    private var baseline: LedPreset? = null
    private var previous: Decision? = null
    private var lastLegacySelection: AppProfileSelection.Result? = null

    fun setBaseline(preset: LedPreset) {
        baseline = preset
        previous = null
        lastLegacySelection = null
    }

    fun reset() { previous = null }

    fun updateBaseline(intent: Intent) {
        val original = baseline ?: return
        val updated = SceneBaseline.fromIntent(intent, original.id, original.name)
        if (updated != original) {
            baseline = updated
            reset()
        }
    }

    fun evaluate(context: Context, batteryPercent: Int?, isCharging: Boolean): Decision? {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(-1)
        val temporary = store.getTemporaryScene(SystemClock.elapsedRealtime(), bootCount)
        val presets = repository.list()
        val byId = presets.associateBy { it.id }
        // Legacy name-based APIs resolve the first stored match. Imports and
        // managed presets may share a name, so keep that existing behaviour.
        val byName = buildMap<String, LedPreset> {
            presets.forEach { preset -> if (preset.name !in this) put(preset.name, preset) }
        }
        val rules = if (store.isEnabled) store.loadRules() else emptyList()
        val needsForeground = appProfiles.isEnabled || rules.any { it.enabled && it.target != SceneTarget.ANY }
        val hasUsageAccess = needsForeground && appProfiles.hasUsageStatsPermission(context)
        val foreground = if (hasUsageAccess) appProfiles.getForegroundPackage(context) else null
        val home = foreground != null && appProfiles.isHomePackage(context, foreground)
        // A brief event-query gap retains the last legacy selection, whereas revoked
        // access immediately stops using app-specific information.
        val legacy = if (appProfiles.isEnabled && hasUsageAccess) {
            appProfiles.resolveSelection(context, foreground)?.also { lastLegacySelection = it }
                ?: lastLegacySelection
        } else {
            lastLegacySelection = null
            null
        }
        val evaluation = SceneEvaluator.evaluate(
            rules,
            if (store.isEnabled) store.loadGroups() else emptyList(),
            SceneContext(
                LocalDateTime.now(), foreground,
                foreground != null && !home && appProfiles.isGamePackage(context, foreground),
                home, batteryPercent, isCharging
            ),
            byId.keys
        )
        val choice = SceneSelectionPolicy.choose(
            temporary?.presetId?.takeIf { it in byId },
            legacy?.source,
            legacy?.presetName?.let { byName[it]?.id },
            evaluation,
            baseline?.id
        )
        val preset = if (choice.source == SceneSelectionPolicy.Source.MANUAL) baseline else byId[choice.presetId]
        val limit = SceneSelectionPolicy.brightnessLimit(choice, evaluation)
        val label = when (choice.source) {
            SceneSelectionPolicy.Source.TEMPORARY -> context.getString(R.string.scene_runtime_temporary, preset?.name.orEmpty())
            SceneSelectionPolicy.Source.APP_PROFILE -> context.getString(R.string.scene_runtime_app_profile)
            SceneSelectionPolicy.Source.RULE -> evaluation.presetRule?.name.orEmpty()
            SceneSelectionPolicy.Source.GAME_SCENE -> context.getString(R.string.scene_runtime_game)
            SceneSelectionPolicy.Source.DEFAULT -> context.getString(R.string.scene_runtime_default)
            SceneSelectionPolicy.Source.MANUAL -> context.getString(R.string.scene_runtime_manual)
        }
        val modifiers = evaluation.matchedRuleNames.filter { it != evaluation.presetRule?.name }
        val reason = buildList {
            add(label)
            addAll(modifiers)
            limit?.let { add(context.getString(R.string.scene_runtime_limit, it)) }
        }.joinToString(" · ")
        val oldPreset = previous?.preset
        val lightingChanged = previous == null || (oldPreset == null) != (preset == null) ||
            oldPreset != null && preset != null && PresetController.hasLightingChanges(oldPreset, preset)
        val decision = Decision(preset, preset?.name, lightingChanged, limit, reason)
        if (previous?.preset == preset && previous?.brightnessLimitPercent == limit && previous?.reason == reason) return null
        previous = decision
        return decision
    }
}

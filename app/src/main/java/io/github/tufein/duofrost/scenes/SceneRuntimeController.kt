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
    private val appProfiles: AppProfileManager,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val localTime: () -> LocalDateTime = LocalDateTime::now
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
    private val sceneForeground = SceneForegroundTracker()

    val needsForegroundMonitoring: Boolean
        get() = appProfiles.isEnabled || store.isEnabled && store.loadRules().any {
            it.enabled && it.target != SceneTarget.ANY
        }

    fun setBaseline(preset: LedPreset) {
        baseline = preset
        previous = null
        lastLegacySelection = null
        sceneForeground.clear()
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
        val nowElapsed = elapsedRealtime()
        val temporary = store.getTemporaryScene(nowElapsed, bootCount)
        val presets = repository.list()
        val byId = presets.associateBy { it.id }
        // Legacy name-based APIs resolve the first stored match. Imports and
        // managed presets may share a name, so keep that existing behaviour.
        val byName = buildMap<String, LedPreset> {
            presets.forEach { preset -> if (preset.name !in this) put(preset.name, preset) }
        }
        val scenesEnabled = store.isEnabled
        val rules = if (scenesEnabled) store.loadRules() else emptyList()
        val scenesNeedForeground = rules.any { it.enabled && it.target != SceneTarget.ANY }
        val needsForeground = appProfiles.isEnabled || scenesNeedForeground
        val hasUsageAccess = needsForeground && appProfiles.hasUsageStatsPermission(context)
        val foreground = if (hasUsageAccess) appProfiles.getForegroundPackage(context) else null
        val stableForeground = sceneForeground.resolve(foreground, nowElapsed, scenesNeedForeground && hasUsageAccess)
        val home = stableForeground != null && appProfiles.isHomePackage(context, stableForeground)
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
            if (scenesEnabled) store.loadGroups() else emptyList(),
            SceneContext(
                localTime(), stableForeground,
                stableForeground != null && !home && appProfiles.isGamePackage(context, stableForeground),
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

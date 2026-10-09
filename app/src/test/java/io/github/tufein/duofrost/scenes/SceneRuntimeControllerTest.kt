package io.github.tufein.duofrost.scenes

import android.content.Context
import android.content.Intent
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SceneRuntimeControllerTest {
    private lateinit var context: Context
    private lateinit var repository: PresetRepository
    private lateinit var store: SceneStore
    private lateinit var runtime: SceneRuntimeController
    private lateinit var baseline: LedPreset
    private lateinit var scene: LedPreset

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 1)
        repository = PresetRepository(prefs)
        baseline = preset("base", "Manual", 0x102030).copy(brightness = 71)
        scene = preset("scene", "Ocean", 0x203040)
        repository.save(listOf(baseline, scene))
        store = SceneStore(prefs)
        runtime = SceneRuntimeController(prefs, AppProfileManager(prefs))
        runtime.setBaseline(baseline)
    }

    private fun preset(id: String, name: String, color: Int) = LedPreset(
        name, LedAnimationType.STATIC, PerformanceProfile.HIGH, color,
        brightness = 180, speed = 0.5f, smoothness = 0.5f, id = id
    )

    private fun evaluate() = runtime.evaluate(context, 60, false)

    @Test fun manualLightingStillAppliesWithAllAutomationDisabled() {
        assertEquals(baseline, evaluate()!!.preset)
        assertNull(evaluate())
        val changed = baseline.copy(color = 0x990055)
        runtime.setBaseline(changed)
        assertEquals(changed, evaluate()!!.preset)
    }

    @Test fun timeOnlyRulesNeedNoUsageAccessAndCannotStartLighting() {
        store.saveRules(listOf(SceneRule("rule", "Ocean anytime", presetId = scene.id)))
        store.isEnabled = true
        assertEquals(scene, evaluate()!!.preset)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    @Test fun duplicateNamesKeepFirstLegacyMappingAndFallbackPreset() {
        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        val first = scene.copy(isAppProfileDefault = true)
        val duplicate = scene.copy(id = "imported-duplicate", color = 0x998877)
        repository.save(listOf(baseline, first, duplicate))
        val profiles = AppProfileManager(prefs)
        profiles.isEnabled = true
        profiles.setMapping("com.example.game", scene.name)
        shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            .addEvent("com.example.game", System.currentTimeMillis() - 1, UsageEvents.Event.ACTIVITY_RESUMED)

        // The scene runtime must match the old first-match name API even while
        // Smart Scenes is off and an imported preset repeats a stored name.
        assertEquals(first, profiles.checkForSwitch(context)!!.preset)
        assertEquals(first, evaluate()!!.preset)
        profiles.removeMapping("com.example.game")
        assertEquals(first, evaluate()!!.preset)
        assertFalse(store.isEnabled)
    }

    @Test fun disablingScenesRestoresExactUnsavedBaseline() {
        val unsaved = baseline.copy(color = 0x993377, speed = 0.77f)
        runtime.setBaseline(unsaved)
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id)))
        store.isEnabled = true
        assertEquals(scene, evaluate()!!.preset)
        store.isEnabled = false
        assertEquals(unsaved, evaluate()!!.preset)
    }

    @Test fun disablingDuringOverlayRemainsEligibleAfterReset() {
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id)))
        store.isEnabled = true
        evaluate()
        store.isEnabled = false
        assertEquals(baseline, evaluate()!!.preset)
        // An overlay may consume the decision without applying it. Reset at its
        // end must still restore manual output, even with automation disabled.
        runtime.reset()
        assertEquals(baseline, evaluate()!!.preset)
    }

    @Test fun dimmingOnlyEditDoesNotRestartAnUnchangedEffect() {
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id),
            SceneRule("dim", "Night", maxBrightnessPercent = 50)))
        store.isEnabled = true
        evaluate()
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id),
            SceneRule("dim", "Night", maxBrightnessPercent = 20)))
        val decision = evaluate()!!
        assertEquals(20, decision.brightnessLimitPercent)
        assertFalse(decision.presetChanged)
    }

    @Test fun renamingPresetKeepsItsRuleAndDoesNotRestartCapture() {
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id)))
        store.isEnabled = true
        evaluate()
        repository.save(listOf(baseline, scene.copy(name = "Renamed")))
        val decision = evaluate()!!
        assertEquals("Renamed", decision.presetName)
        assertFalse(decision.presetChanged)
    }

    @Test fun resumeAutomationChoosesCurrentRulesInsteadOfOldSnapshot() {
        store.saveRules(listOf(SceneRule("rule", "Ocean", presetId = scene.id)))
        store.isEnabled = true
        assertTrue(store.setTemporaryScene(baseline.id, 15, SystemClock.elapsedRealtime(), 1))
        assertEquals(baseline, evaluate()!!.preset)
        val replacement = preset("new", "Evening", 0x2200aa)
        repository.save(listOf(baseline, scene, replacement))
        store.saveRules(listOf(SceneRule("new-rule", "Evening", presetId = replacement.id)))
        store.clearTemporaryScene()
        assertEquals(replacement, evaluate()!!.preset)
    }

    @Test fun explicitStopClearsTemporaryChoiceWithoutEnablingAutomation() {
        assertTrue(store.setTemporaryScene(scene.id, 15, SystemClock.elapsedRealtime(), 1))
        ServiceRecoveryStore.markStopped(context)
        assertNull(store.getTemporaryScene(SystemClock.elapsedRealtime(), 1))
        assertFalse(store.isEnabled)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    @Test fun snapshotPreservesUnsavedColorsAndOptionalColorAbsence() {
        val intent = Intent().putExtra("animationType", "STATIC")
            .putExtra("animationColor", 0x113355).putExtra("animationRightColor", 0x552211)
            .putExtra("brightness", 41).putExtra("speed", 0.73f)
            .putExtra("batteryLowColorOverride", Int.MIN_VALUE)
        val snapshot = SceneBaseline.fromIntent(intent, "manual", "Unsaved")
        assertEquals(0x113355, snapshot.color)
        assertEquals(0x552211, snapshot.rightColor)
        assertEquals(41, snapshot.brightness)
        assertEquals(0.73f, snapshot.speed)
        assertNull(snapshot.batteryLowColorOverride)
    }
}

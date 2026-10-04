package io.github.tufein.duofrost

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BehaviorSettingsBackupTest {
    @Test fun behaviorBooleansRequireActualBooleanValues() {
        assertEquals(true, BehaviorSettingsBackup.decode("keep_running_enabled", "boolean", true))
        assertEquals(false, BehaviorSettingsBackup.decode("auto_start_heimdall", "boolean", false))
        assertNull(BehaviorSettingsBackup.decode("keep_running_enabled", "boolean", "true"))
        assertNull(BehaviorSettingsBackup.decode("keep_running_enabled", "int", 1))
    }
    @Test fun percentageLimitsRoundTripIncludingBlackout() {
        for (key in listOf("led_output_maximum_percent", "led_output_battery_saver_percent", "led_output_screen_off_percent")) {
            for (value in 0..100) assertEquals(value, BehaviorSettingsBackup.decode(key, "int", value))
            assertNull(BehaviorSettingsBackup.decode(key, "int", -1))
            assertNull(BehaviorSettingsBackup.decode(key, "int", 101))
        }
    }
    @Test fun wrongNumericTypesCannotCorruptPreferences() {
        for (value in listOf(10L, 10f, 10.0, "10", true, JSONObject.NULL)) {
            assertNull(BehaviorSettingsBackup.decode("led_output_maximum_percent", "int", value))
        }
    }
    @Test fun runtimeCaptureAndDeviceGrantsCannotBeImported() {
        for (key in listOf("desired_running", "output_muted", "configuration", "deadline", "data",
            "projectionTokenId", "external.lease", "accessibility_enabled", "presets_json")) {
            assertFalse(key in BehaviorSettingsBackup.keys)
            assertNull(BehaviorSettingsBackup.decode(key, "boolean", true))
        }
    }
    @Test fun settingsCanBeSelectedWithoutOtherCategories() {
        assertTrue(BackupArchiveTransfer.CategoryOptions(false, false, false, true).hasAtLeastOneCategory())
        assertFalse(BackupArchiveTransfer.CategoryOptions(false, false, false, false).hasAtLeastOneCategory())
    }
    @Test fun legacyManifestWithoutSettingsPreservesExistingBehavior() {
        val options = BackupArchiveTransfer.resolveEffectiveOptions(JSONObject(), BackupArchiveTransfer.CategoryOptions())
        assertFalse(options.settings)
        assertTrue(options.themes && options.profiles && options.images)
        val manifest = JSONObject("""{"categories":{"themes":true,"profiles":true,"images":true}}""")
        assertFalse(BackupArchiveTransfer.resolveEffectiveOptions(manifest, BackupArchiveTransfer.CategoryOptions()).settings)
    }
    @Test fun newArchiveSettingsRequireBothUserChoiceAndManifestOptIn() {
        val manifest = JSONObject("""{"categories":{"settings":true}}""")
        assertTrue(BackupArchiveTransfer.resolveEffectiveOptions(manifest, BackupArchiveTransfer.CategoryOptions()).settings)
        assertFalse(BackupArchiveTransfer.resolveEffectiveOptions(manifest,
            BackupArchiveTransfer.CategoryOptions(settings = false)).settings)
        manifest.getJSONObject("categories").put("settings", "true")
        assertFalse(BackupArchiveTransfer.resolveEffectiveOptions(manifest, BackupArchiveTransfer.CategoryOptions()).settings)
    }
}

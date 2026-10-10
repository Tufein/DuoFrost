package io.github.tufein.duofrost

import android.content.Context
import android.net.Uri
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresetLibraryBackupTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun profileBackupRestoresCollectionsAndFavoritesButNeverPendingUndo() {
        val preset = LedPreset("Ocean", LedAnimationType.STATIC, PerformanceProfile.HIGH,
            0x123456, brightness = 180, speed = .5f, smoothness = .5f, id = "ocean")
        PresetRepository(prefs).save(listOf(preset))
        val library = PresetLibraryStore(prefs)
        library.setFavorite(preset.id, true)
        val collection = library.createCollection("Evening")!!
        library.setMembership(preset.id, collection.id, true)
        val rawLibrary = prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null)
        val rawPresets = prefs.getString("presets_json", null)
        prefs.edit().putString(PresetUndoStore.PREF_KEY_UNDO, "private transient snapshot").commit()
        val archive = File(context.cacheDir, "library-backup.bifrost_backup")
        val options = BackupArchiveTransfer.CategoryOptions(themes = false, profiles = true,
            images = false, settings = false)
        try {
            BackupArchiveTransfer.exportToUri(context, Uri.fromFile(archive), options)
            ZipFile(archive).use { zip ->
                val root = JSONObject(zip.getInputStream(zip.getEntry("prefs.json")).bufferedReader().readText())
                val entries = root.getJSONArray("items")
                val keys = (0 until entries.length()).map { entries.getJSONObject(it).getString("key") }
                assertTrue(PresetLibraryStore.PREF_KEY_LIBRARY in keys)
                assertFalse(PresetUndoStore.PREF_KEY_UNDO in keys)
            }
            prefs.edit().putString("presets_json", "[]").remove(PresetLibraryStore.PREF_KEY_LIBRARY)
                .putString(PresetUndoStore.PREF_KEY_UNDO, "stale snapshot").commit()
            val result = BackupArchiveTransfer.importFromUri(context, Uri.fromFile(archive), options)
            assertTrue(result.errors.toString(), result.errors.isEmpty())
            assertEquals(rawPresets, prefs.getString("presets_json", null))
            assertEquals(rawLibrary, prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null))
            assertFalse(prefs.contains(PresetUndoStore.PREF_KEY_UNDO))
            assertEquals(setOf(preset.id), PresetLibraryStore(prefs).state().collections.single().presetIds)
        } finally { archive.delete() }
    }

    @Test fun themeOnlyRestoreLeavesLibraryAndItsUndoUntouched() {
        val archive = File(context.cacheDir, "theme-only.bifrost_backup")
        val options = BackupArchiveTransfer.CategoryOptions(themes = true, profiles = false,
            images = false, settings = false)
        prefs.edit().putString("selected_ui_theme", "classic").commit()
        try {
            BackupArchiveTransfer.exportToUri(context, Uri.fromFile(archive), options)
            prefs.edit().putString(PresetLibraryStore.PREF_KEY_LIBRARY, "kept library")
                .putString(PresetUndoStore.PREF_KEY_UNDO, "kept snapshot").commit()
            val result = BackupArchiveTransfer.importFromUri(context, Uri.fromFile(archive), options)
            assertTrue(result.errors.toString(), result.errors.isEmpty())
            assertEquals("kept library", prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null))
            assertEquals("kept snapshot", prefs.getString(PresetUndoStore.PREF_KEY_UNDO, null))
        } finally { archive.delete() }
    }
}

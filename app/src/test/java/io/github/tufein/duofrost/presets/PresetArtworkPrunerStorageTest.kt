package io.github.tufein.duofrost

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresetArtworkPrunerStorageTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("pruner", Context.MODE_PRIVATE)

    @Before fun setUp() {
        directory = Files.createTempDirectory("duofrost-pruner-").toFile()
        val application = RuntimeEnvironment.getApplication()
        val preferenceName = "pruner-${UUID.randomUUID()}"
        context = object : ContextWrapper(application) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = application.getSharedPreferences(preferenceName, mode)
        }
    }

    @After fun tearDown() { directory.deleteRecursively() }

    @Test fun oldOrphansAreDeletedWhileCurrentUndoAndNewImportArtworkRemain() {
        val current = icon("current.png", old = true)
        val recoverable = icon("deleted.png", old = true)
        val orphan = icon("orphan.png", old = true)
        val staged = icon("staged.png", old = false)
        prefs.edit().putString("presets_json", raw("current" to current.name, "deleted" to recoverable.name)).commit()
        val undo = PresetUndoStore(prefs)
        val before = undo.captureSnapshot()!!
        prefs.edit().putString("presets_json", raw("current" to current.name)).commit()
        assertEquals(PresetUndoStore.RecordResult.RECORDED, undo.record(PresetUndoStore.Action.DELETE, before))
        assertEquals(1, PresetArtworkPruner.prune(context, prefs))
        assertFalse(orphan.exists())
        assertTrue(current.exists())
        assertTrue(recoverable.exists())
        assertTrue(staged.exists())
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, undo.restore())
        assertTrue(recoverable.exists())
    }

    @Test fun expiredUndoAllowsCleanupWithoutRemovingCurrentArtwork() {
        val current = icon("current.png", old = true)
        val deleted = icon("deleted.png", old = true)
        prefs.edit().putString("presets_json", raw("current" to current.name, "deleted" to deleted.name)).commit()
        val expiredClock = System.currentTimeMillis() - PresetUndoStore.TTL_MILLIS - 60_000
        val undo = PresetUndoStore(prefs) { expiredClock }
        val before = undo.captureSnapshot()!!
        prefs.edit().putString("presets_json", raw("current" to current.name)).commit()
        assertEquals(PresetUndoStore.RecordResult.RECORDED, undo.record(PresetUndoStore.Action.DELETE, before))
        assertEquals(1, PresetArtworkPruner.prune(context, prefs))
        assertFalse(deleted.exists())
        assertTrue(current.exists())
    }

    @Test fun unknownPresetDataStopsAllCleanupAndLeavesStoredJsonExact() {
        val orphan = icon("orphan.png", old = true)
        val raw = """[{"id":"future","name":"Future","animationType":"STATIC","futureArtwork":"orphan.png"}]"""
        prefs.edit().putString("presets_json", raw).commit()
        assertEquals(0, PresetArtworkPruner.prune(context, prefs))
        assertTrue(orphan.exists())
        assertEquals(raw, prefs.getString("presets_json", null))
    }

    private fun icon(name: String, old: Boolean): File = File(directory, "preset_icons/$name").apply {
        parentFile!!.mkdirs()
        writeText("image fixture")
        if (old) assertTrue(setLastModified(System.currentTimeMillis() - PresetUndoStore.TTL_MILLIS - 60_000))
    }

    private fun raw(vararg images: Pair<String, String>): String = JSONArray(images.map { (id, image) ->
        JSONObject().put("id", id).put("name", id).put("animationType", "STATIC").put("customImageFileName", image)
    }).toString()
}

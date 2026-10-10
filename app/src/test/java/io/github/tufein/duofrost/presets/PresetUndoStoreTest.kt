package io.github.tufein.duofrost

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PresetUndoStoreTest {
    private val values = mutableMapOf<String, Any?>()
    private val prefs = memoryPreferences(values)
    private var now = 1_000_000L
    private val store = PresetUndoStore(prefs) { now }
    private val original = """[ {"id":"retro","name":"Before","customImageFileName":"art.png","future":{"flag":true}},42 ]"""
    private val deleted = "[42]"

    @Test fun deletionRestoresExactRawPresetsAndMetadataTogetherAndConsumesUndo() {
        values["presets_json"] = original
        val library = PresetLibraryStore(prefs)
        library.setFavorite("retro", true)
        val collection = library.createCollection("Games")!!
        library.setMembership("retro", collection.id, true)
        val before = store.captureSnapshot()!!
        values["presets_json"] = deleted
        library.removePreset("retro")
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.DELETE, before))
        assertEquals(PresetUndoStore.Action.DELETE, store.pending()!!.action)
        assertEquals(setOf("art.png"), store.retainedArtworkFileNames())
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, store.restore())
        assertEquals(original, values["presets_json"])
        assertEquals(before.libraryJson, values[PresetLibraryStore.PREF_KEY_LIBRARY])
        assertTrue(library.isFavorite("retro"))
        assertEquals(setOf("retro"), library.state().collections.single().presetIds)
        assertFalse(values.containsKey(PresetUndoStore.PREF_KEY_UNDO))
        assertEquals(PresetUndoStore.RestoreResult.NOTHING_PENDING, store.restore())
    }

    @Test fun importUndoKeepsTheCompleteBeforeStateAcrossStoreRecreation() {
        values["presets_json"] = original
        val before = store.captureSnapshot()!!
        values["presets_json"] = """[{"id":"imported","name":"Imported","customImageFileName":"other.png"}]"""
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.IMPORT, before))
        val reopened = PresetUndoStore(prefs) { now }
        assertEquals(PresetUndoStore.Action.IMPORT, reopened.pending()!!.action)
        assertEquals(setOf("art.png"), reopened.retainedArtworkFileNames())
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, reopened.restore())
        assertEquals(original, values["presets_json"])
        assertFalse(values.containsKey(PresetLibraryStore.PREF_KEY_LIBRARY))
    }

    @Test fun importUndoRestoresExactMappingsAlongWithPresets() {
        values["presets_json"] = original
        val originalMappings = "{ \"com.game\" : \"Before\", \"com.second\" : \"Second\" }"
        values["app_profile_mappings"] = originalMappings
        val before = store.captureSnapshot()!!
        values["presets_json"] = """[{"id":"new","name":"New"}]"""
        values["app_profile_mappings"] = """{"com.game":"New"}"""
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.IMPORT, before))
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, store.restore())
        assertEquals(original, values["presets_json"])
        assertEquals(originalMappings, values["app_profile_mappings"])
    }

    @Test fun mappingsChangedAfterImportBlockUndoInsteadOfOverwritingAnExternalUpdate() {
        recordDelete()
        values["app_profile_mappings"] = """{"com.new.game":"Another preset"}"""
        val afterMappingUpdate = values.toMap()
        assertNull(store.pending())
        assertEquals(PresetUndoStore.RestoreResult.CONFLICT, store.restore())
        assertEquals(afterMappingUpdate, values)
    }

    @Test fun wrongTypedOrMalformedMappingsCannotBeSnapshotted() {
        values["presets_json"] = original
        for (bad in listOf<Any>(42, "[]", "invalid", """{"com.game":42}""", """{"com.game":""}""")) {
            values["app_profile_mappings"] = bad
            assertNull(store.captureSnapshot())
        }
        values["app_profile_mappings"] = "{}"
        assertNotNull(store.captureSnapshot())
    }

    @Test fun missingDocumentsAreRestoredAsMissingRatherThanEmptyStrings() {
        val before = store.captureSnapshot()!!
        values["presets_json"] = "[]"
        PresetLibraryStore(prefs).setFavorite("retro", true)
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.IMPORT, before))
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, store.restore())
        assertFalse(values.containsKey("presets_json"))
        assertFalse(values.containsKey(PresetLibraryStore.PREF_KEY_LIBRARY))
    }

    @Test fun laterPresetEditsOrPluginInstallsInvalidateUndoWithoutOverwritingThem() {
        recordDelete()
        val later = """[{"id":"plugin","name":"Installed afterwards"}]"""
        values["presets_json"] = later
        assertNull(store.pending())
        assertTrue(store.retainedArtworkFileNames().isEmpty())
        assertEquals(PresetUndoStore.RestoreResult.CONFLICT, store.restore())
        assertEquals(later, values["presets_json"])
    }

    @Test fun laterFavoritesOrCollectionEditsInvalidateUndoWithoutDiscardingMetadata() {
        recordDelete()
        PresetLibraryStore(prefs).setFavorite("another", true)
        val afterMetadataEdit = values.toMap()
        assertNull(store.pending())
        assertEquals(PresetUndoStore.RestoreResult.CONFLICT, store.restore())
        assertEquals(afterMetadataEdit, values)
    }

    @Test fun LivePresetSelectionAndSettingsChangesDoNotInvalidateLibraryUndo() {
        recordDelete()
        values["last_preset_name"] = "Automatically selected game"
        values["brightness"] = 75
        values["smart_scenes_enabled"] = true
        assertNotNull(store.pending())
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, store.restore())
        assertEquals("Automatically selected game", values["last_preset_name"])
        assertEquals(75, values["brightness"])
        assertEquals(true, values["smart_scenes_enabled"])
    }

    @Test fun ttlExpiresAtDeadlineAndClockRollbackAlsoFailsClosed() {
        recordDelete()
        now += PresetUndoStore.TTL_MILLIS - 1
        assertNotNull(store.pending())
        now += 1
        assertNull(store.pending())
        assertEquals(PresetUndoStore.RestoreResult.EXPIRED, store.restore())
        assertEquals(deleted, values["presets_json"])
        values.remove(PresetUndoStore.PREF_KEY_UNDO)
        now = 1_000_000
        recordDelete()
        now -= 1
        assertNull(store.pending())
        assertEquals(PresetUndoStore.RestoreResult.EXPIRED, store.restore())
    }

    @Test fun newestDestructiveActionReplacesOlderUndoOnly() {
        recordDelete()
        val beforeImport = store.captureSnapshot()!!
        val imported = """[{"id":"new","name":"Imported"}]"""
        values["presets_json"] = imported
        now += 5
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.IMPORT, beforeImport))
        assertEquals(PresetUndoStore.Action.IMPORT, store.pending()!!.action)
        assertEquals(PresetUndoStore.RestoreResult.RESTORED, store.restore())
        assertEquals(deleted, values["presets_json"])
    }

    @Test fun unchangedOrStaleExpectedAfterCannotCreateANewUndo() {
        values["presets_json"] = original
        val before = store.captureSnapshot()!!
        assertEquals(PresetUndoStore.RecordResult.NOTHING_CHANGED, store.record(PresetUndoStore.Action.DELETE, before))
        val after = PresetUndoStore.Snapshot(deleted, null)
        assertEquals(PresetUndoStore.RecordResult.CONFLICT, store.record(PresetUndoStore.Action.DELETE, before, after))
        assertNull(store.pending())
        assertFalse(values.containsKey(PresetUndoStore.PREF_KEY_UNDO))
    }

    @Test fun malformedWrongTypedOrOversizedPresetDocumentsCannotBeSnapshotted() {
        listOf<Any>(42, "bad JSON", "{}", JSONArray((0..PresetUndoStore.MAX_PRESETS).toList()).toString(),
            "[\"" + "x".repeat(PresetUndoStore.MAX_SNAPSHOT_BYTES) + "\"]").forEach { bad ->
            values["presets_json"] = bad
            assertNull(store.captureSnapshot())
        }
        values["presets_json"] = original
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = """{"schemaVersion":9,"favorites":[],"collections":[]}"""
        assertNull(store.captureSnapshot())
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = 42
        assertNull(store.captureSnapshot())
    }

    @Test fun combinedBeforeAndAfterByteLimitDoesNotStorePartialUndo() {
        val first = "[\"" + "é".repeat(300_000) + "\"]"
        val second = "[\"different" + "é".repeat(300_000) + "\"]"
        values["presets_json"] = first
        val before = store.captureSnapshot()!!
        values["presets_json"] = second
        assertEquals(PresetUndoStore.RecordResult.TOO_LARGE, store.record(PresetUndoStore.Action.IMPORT, before))
        assertFalse(values.containsKey(PresetUndoStore.PREF_KEY_UNDO))
        assertEquals(second, values["presets_json"])
    }

    @Test fun damagedRecordsCannotExecuteOrRetainArtwork() {
        recordDelete()
        val validRaw = values[PresetUndoStore.PREF_KEY_UNDO] as String
        val damaged = listOf<Any>(
            42, "invalid", JSONObject(validRaw).put("schemaVersion", 2).toString(),
            JSONObject(validRaw).put("expiresAtMillis", now + PresetUndoStore.TTL_MILLIS + 1).toString(),
            JSONObject(validRaw).put("createdAtMillis", "1000000").toString(),
            JSONObject(validRaw).put("action", "START_LIGHTING").toString(),
            JSONObject(validRaw).put("before", JSONObject().put("presetsJson", original)).toString()
        )
        damaged.forEach { raw ->
            values[PresetUndoStore.PREF_KEY_UNDO] = raw
            assertNull(store.pending())
            assertTrue(store.retainedArtworkFileNames().isEmpty())
            assertEquals(PresetUndoStore.RestoreResult.INVALID_SNAPSHOT, store.restore())
            assertEquals(deleted, values["presets_json"])
        }
    }

    @Test fun invalidClockAndWriteFailuresReturnExplicitResults() {
        values["presets_json"] = original
        val before = store.captureSnapshot()!!
        values["presets_json"] = deleted
        now = Long.MAX_VALUE
        assertEquals(PresetUndoStore.RecordResult.INVALID_SNAPSHOT, store.record(PresetUndoStore.Action.DELETE, before))
        now = -1
        assertEquals(PresetUndoStore.RecordResult.INVALID_SNAPSHOT, store.record(PresetUndoStore.Action.DELETE, before))
        now = 1_000_000
        val failing = PresetUndoStore(memoryPreferences(values, writesSucceed = false)) { now }
        assertEquals(PresetUndoStore.RecordResult.WRITE_FAILED, failing.record(PresetUndoStore.Action.DELETE, before))
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.DELETE, before))
        assertEquals(PresetUndoStore.RestoreResult.WRITE_FAILED, failing.restore())
        assertEquals(deleted, values["presets_json"])
    }

    @Test fun clearingUndoLeavesLibraryAndOtherPreferencesUntouched() {
        recordDelete()
        values["setting"] = true
        val beforeClear = values.filterKeys { it != PresetUndoStore.PREF_KEY_UNDO }
        assertTrue(store.clear())
        assertEquals(beforeClear, values)
        assertNull(store.pending())
        assertEquals(setOf(PresetUndoStore.PREF_KEY_UNDO), PresetUndoStore.TRANSIENT_PREF_KEYS)
        assertEquals(setOf(PresetLibraryStore.PREF_KEY_LIBRARY), PresetLibraryStore.BACKUP_PREF_KEYS)
    }

    private fun recordDelete() {
        values["presets_json"] = original
        val before = store.captureSnapshot()!!
        values["presets_json"] = deleted
        assertEquals(PresetUndoStore.RecordResult.RECORDED, store.record(PresetUndoStore.Action.DELETE, before))
    }

    private fun memoryPreferences(data: MutableMap<String, Any?>, writesSucceed: Boolean = true): SharedPreferences =
        Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> data[args!![0]] ?: args[1]
                "getAll" -> data.toMap()
                "contains" -> data.containsKey(args!![0])
                "edit" -> memoryEditor(data, writesSucceed)
                "hashCode" -> System.identityHashCode(data)
                "equals" -> false
                else -> null
            }
        } as SharedPreferences

    private fun memoryEditor(data: MutableMap<String, Any?>, writesSucceed: Boolean): SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        val removed = mutableSetOf<String>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { changes[args!![0] as String] = args[1]; removed.remove(args[0]); editor }
                "remove" -> { removed += args!![0] as String; changes.remove(args[0]); editor }
                "apply", "commit" -> {
                    if (writesSucceed) { removed.forEach(data::remove); data.putAll(changes) }
                    method.name == "commit" && writesSucceed
                }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}

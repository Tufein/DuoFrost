package io.github.tufein.duofrost

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PresetLibraryStoreTest {
    private val values = mutableMapOf<String, Any?>()
    private val prefs = memoryPreferences(values)
    private val store = PresetLibraryStore(prefs)

    @Test fun favoritesAndCollectionsRoundTripByIdentity() {
        assertFalse(store.isReadOnly)
        assertEquals(PresetLibraryStore.LibraryState(), store.state())
        assertTrue(store.setFavorite("retro-id", true))
        val collection = store.createCollection("  Retro games  ")!!
        assertTrue(store.setMembership("retro-id", collection.id, true))
        assertTrue(store.setMembership("second-id", collection.id, true))
        val restored = PresetLibraryStore(prefs)
        assertTrue(restored.isFavorite("retro-id"))
        assertEquals("Retro games", restored.state().collections.single().name)
        assertEquals(setOf("retro-id", "second-id"), restored.state().collections.single().presetIds)
        assertEquals(setOf(PresetLibraryStore.PREF_KEY_LIBRARY), values.keys)
    }

    @Test fun favoriteAndMembershipChangesAreIdempotent() {
        val collection = store.createCollection("Games")!!
        assertTrue(store.setFavorite("preset", true))
        val favoriteRaw = values.toMap()
        assertTrue(store.setFavorite("preset", true))
        assertEquals(favoriteRaw, values)
        assertTrue(store.setMembership("preset", collection.id, true))
        assertTrue(store.setMembership("preset", collection.id, true))
        assertEquals(setOf("preset"), store.state().collections.single().presetIds)
        assertTrue(store.setFavorite("preset", false))
        assertTrue(store.setMembership("preset", collection.id, false))
        assertFalse(store.isFavorite("preset"))
        assertTrue(store.state().collections.single().presetIds.isEmpty())
    }

    @Test fun collectionRenameKeepsMembershipAndDeletingItKeepsFavorite() {
        val collection = store.createCollection("Games")!!
        store.setMembership("preset", collection.id, true)
        store.setFavorite("preset", true)
        assertTrue(store.renameCollection(collection.id, "Evening"))
        assertEquals(collection.copy(name = "Evening", presetIds = setOf("preset")), store.state().collections.single())
        assertTrue(store.deleteCollection(collection.id))
        assertTrue(store.state().collections.isEmpty())
        assertTrue(store.isFavorite("preset"))
    }

    @Test fun deletionRemovesOnlyThatPresetMetadataAndLeavesEmptyCollections() {
        val collection = store.createCollection("Games")!!
        listOf("deleted", "kept").forEach { store.setFavorite(it, true); store.setMembership(it, collection.id, true) }
        assertTrue(store.removePreset("deleted"))
        assertEquals(setOf("kept"), store.state().favorites)
        assertEquals(setOf("kept"), store.state().collections.single().presetIds)
        assertTrue(store.removePreset("kept"))
        assertTrue(store.state().collections.single().presetIds.isEmpty())
    }

    @Test fun batchDeleteRemovesOnlyRequestedIdsAcrossFavoritesAndEveryCollection() {
        val first = store.createCollection("First")!!
        val second = store.createCollection("Second")!!
        listOf("a", "b", "kept").forEach { id ->
            store.setFavorite(id, true)
            store.setCollectionsForPreset(id, setOf(first.id, second.id))
        }
        val before = values.toMap()
        assertFalse(store.removePresets(setOf("a", "bad id")))
        assertEquals(before, values)
        assertTrue(store.removePresets(setOf("a", "b")))
        assertEquals(setOf("kept"), store.state().favorites)
        assertTrue(store.state().collections.all { it.presetIds == setOf("kept") })
    }

    @Test fun collectionDialogSavesCompleteMembershipAtomicallyAndRejectsUnknownIds() {
        val first = store.createCollection("First")!!
        val second = store.createCollection("Second")!!
        store.setMembership("kept", first.id, true)
        assertTrue(store.setCollectionsForPreset("preset", setOf(first.id, second.id)))
        assertTrue(store.state().collections.all { "preset" in it.presetIds })
        val snapshot = values.toMap()
        assertFalse(store.setCollectionsForPreset("preset", setOf(first.id, "missing")))
        assertEquals(snapshot, values)
        assertTrue(store.setCollectionsForPreset("preset", setOf(second.id)))
        assertEquals(setOf("kept"), store.state().collections.first().presetIds)
        assertEquals(setOf("preset"), store.state().collections.last().presetIds)
        assertTrue(store.setCollectionsForPreset("preset", emptySet()))
        assertTrue(store.state().collections.none { "preset" in it.presetIds })
    }

    @Test fun cachedReadsObserveEditsRemovalAndWrongTypesAndReturnDefensiveState() {
        store.setFavorite("first", true)
        val collection = store.createCollection("Games")!!
        store.setMembership("first", collection.id, true)
        val returned = store.state()
        // A caller-owned list/set cannot modify a subsequent cached read.
        (returned.collections as? MutableList)?.clear()
        assertEquals(1, store.state().collections.size)
        assertTrue(store.isFavorite("first"))
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = """{"schemaVersion":1,"favorites":["second"],"collections":[]}"""
        assertFalse(store.isFavorite("first"))
        assertTrue(store.isFavorite("second"))
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = 42
        assertTrue(store.isReadOnly)
        assertFalse(store.isFavorite("second"))
        values.remove(PresetLibraryStore.PREF_KEY_LIBRARY)
        assertFalse(store.isReadOnly)
        assertTrue(store.state().favorites.isEmpty())
    }

    @Test fun renamingOrReorderingPresetNeverMovesMetadataToSameNamedPreset() {
        store.setFavorite("first", true)
        val first = preset("first", "Same")
        val second = preset("second", "Same")
        val rows = store.filter(listOf(second, first.copy(name = "Renamed")), favoritesOnly = true)
        assertEquals(listOf(1), rows.map { it.index })
        assertEquals("first", rows.single().preset.id)
        assertEquals("Renamed", rows.single().preset.name)
    }

    @Test fun filterComposesAccentInsensitiveSearchFavoriteAndCollectionWithoutLosingIndices() {
        val collection = store.createCollection("Evening")!!
        val presets = listOf(preset("a", "Start"), preset("b", "Été"), preset("c", "Été copied"), preset("d", "End"))
        store.setFavorite("b", true)
        store.setFavorite("c", true)
        store.setMembership("b", collection.id, true)
        store.setMembership("d", collection.id, true)
        assertEquals(listOf(1), store.filter(presets, "ete", true, collection.id).map { it.index })
        assertTrue(store.filter(presets, collectionId = "missing").isEmpty())
        assertEquals(listOf(0, 1, 2, 3), store.filter(presets).map { it.index })
    }

    @Test fun favoritesFirstKeepsBothGroupsOriginalOrderAndDoesNotMutateStoredPresets() {
        val presets = listOf(preset("a", "A"), preset("b", "B"), preset("c", "C"), preset("d", "D"))
        values["presets_json"] = "keep this exact raw data"
        store.setFavorite("b", true)
        store.setFavorite("d", true)
        assertEquals(listOf(1, 3, 0, 2), store.filter(presets, favoritesFirst = true).map { it.index })
        assertEquals("keep this exact raw data", values["presets_json"])
        assertEquals(listOf("a", "b", "c", "d"), presets.map { it.id })
    }

    @Test fun invalidIdsNamesMissingCollectionsAndDuplicateNamesCannotChangeState() {
        val collection = store.createCollection("Games")!!
        val before = values.toMap()
        assertFalse(store.setFavorite("bad id", true))
        assertFalse(store.setMembership("bad id", collection.id, true))
        assertFalse(store.setMembership("preset", "missing", true))
        assertFalse(store.renameCollection("missing", "Name"))
        assertFalse(store.deleteCollection("missing"))
        assertNull(store.createCollection("games"))
        assertNull(store.createCollection("  "))
        assertNull(store.createCollection("bad\nname"))
        assertNull(store.createCollection("x".repeat(81)))
        assertEquals(before, values)
        val second = store.createCollection("Second")!!
        assertFalse(store.renameCollection(second.id, "GAMES"))
    }

    @Test fun collectionAndPresetIdCountLimitsAreEnforced() {
        repeat(PresetLibraryStore.MAX_COLLECTIONS) { assertNotNull(store.createCollection("Collection $it")) }
        val before = values.toMap()
        assertNull(store.createCollection("Extra"))
        assertEquals(before, values)
        val root = JSONObject(values[PresetLibraryStore.PREF_KEY_LIBRARY] as String)
            .put("favorites", JSONArray((0 until PresetLibraryStore.MAX_IDS).map { "preset-$it" }))
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = root.toString()
        assertFalse(store.setFavorite("extra", true))
        assertEquals(PresetLibraryStore.MAX_IDS, store.state().favorites.size)
    }

    @Test fun futureMalformedOrWrongTypedPreferencesRemainIntactAndReadOnly() {
        val broken = listOf<Any>(
            "not JSON", "[]", "{}", 42,
            """{"schemaVersion":2,"favorites":[],"collections":[],"future":"keep"}""",
            """{"schemaVersion":1,"favorites":["bad id"],"collections":[]}""",
            """{"schemaVersion":1,"favorites":["a","a"],"collections":[]}""",
            """{"schemaVersion":1,"favorites":[],"collections":[{"id":"a","name":"Name","presetIds":42}]}"""
        )
        broken.forEach { raw ->
            values[PresetLibraryStore.PREF_KEY_LIBRARY] = raw
            assertTrue("$raw", store.isReadOnly)
            assertTrue(store.state().favorites.isEmpty())
            assertFalse(store.setFavorite("preset", true))
            assertNull(store.createCollection("Name"))
            assertFalse(store.removePreset("preset"))
            assertEquals(raw, values[PresetLibraryStore.PREF_KEY_LIBRARY])
        }
    }

    @Test fun metadataEditsRetainUnknownRootAndCollectionFields() {
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = """{"schemaVersion":1,"favorites":[],"futureRoot":{"flag":true},"collections":[{"id":"games","name":"Games","presetIds":[],"futureCollection":"keep"}]}"""
        assertTrue(store.setFavorite("retro", true))
        assertTrue(store.setMembership("retro", "games", true))
        assertTrue(store.renameCollection("games", "Evening"))
        val root = JSONObject(values[PresetLibraryStore.PREF_KEY_LIBRARY] as String)
        assertTrue(root.getJSONObject("futureRoot").getBoolean("flag"))
        assertEquals("keep", root.getJSONArray("collections").getJSONObject(0).getString("futureCollection"))
    }

    @Test fun excessiveBytesCannotBeReadOrWritten() {
        val root = JSONObject().put("schemaVersion", 1).put("favorites", JSONArray()).put("collections", JSONArray())
            .put("future", "é".repeat(PresetLibraryStore.MAX_JSON_BYTES / 2))
        values[PresetLibraryStore.PREF_KEY_LIBRARY] = root.toString()
        assertTrue(store.isReadOnly)
        val before = values.toMap()
        assertFalse(store.setFavorite("preset", true))
        assertEquals(before, values)
    }

    private fun preset(id: String, name: String): LedPreset = PresetCodec.decode(JSONObject().put("id", id).put("name", name))

    private fun memoryPreferences(data: MutableMap<String, Any?>): SharedPreferences =
        Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> data[args!![0]] ?: args[1]
                "getAll" -> data.toMap()
                "contains" -> data.containsKey(args!![0])
                "edit" -> memoryEditor(data)
                "hashCode" -> System.identityHashCode(data)
                "equals" -> false
                else -> null
            }
        } as SharedPreferences

    private fun memoryEditor(data: MutableMap<String, Any?>): SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        val removed = mutableSetOf<String>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { changes[args!![0] as String] = args[1]; removed.remove(args[0]); editor }
                "remove" -> { removed += args!![0] as String; changes.remove(args[0]); editor }
                "apply", "commit" -> { removed.forEach(data::remove); data.putAll(changes); method.name == "commit" }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}

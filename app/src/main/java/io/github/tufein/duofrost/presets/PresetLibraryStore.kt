package io.github.tufein.duofrost

import android.content.SharedPreferences
import io.github.tufein.duofrost.ui.PresetLibrarySearch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Library organisation is separate from lighting settings and follows stable preset IDs. */
class PresetLibraryStore(private val prefs: SharedPreferences) {
    private var cachedRaw: Any? = UNREAD
    private var cachedState: LibraryState? = null
    data class Collection(val id: String, val name: String, val presetIds: Set<String> = emptySet())
    data class LibraryState(
        val favorites: Set<String> = emptySet(),
        val collections: List<Collection> = emptyList()
    )
    data class IndexedPreset(val index: Int, val preset: LedPreset)

    /** Malformed or newer data stays intact until a compatible app can read it. */
    val isReadOnly: Boolean
        get() = synchronized(prefs) { readState() == null }

    fun state(): LibraryState = synchronized(prefs) {
        val current = readState() ?: return@synchronized LibraryState()
        // Callers can retain or copy the state without changing the cache's sets.
        current.copy(favorites = current.favorites.toSet(),
            collections = current.collections.map { it.copy(presetIds = it.presetIds.toSet()) })
    }

    fun isFavorite(presetId: String): Boolean = synchronized(prefs) { readState()?.favorites?.contains(presetId) == true }

    fun setFavorite(presetId: String, enabled: Boolean): Boolean {
        if (!PresetIdentity.isValid(presetId)) return false
        return mutate { state -> state.copy(favorites = if (enabled) state.favorites + presetId else state.favorites - presetId) }
    }

    fun createCollection(name: String): Collection? = synchronized(prefs) {
        val state = readState() ?: return@synchronized null
        val cleanedName = validName(name) ?: return@synchronized null
        if (state.collections.size >= MAX_COLLECTIONS || duplicateName(state, cleanedName)) return@synchronized null
        val collection = Collection(PresetIdentity.newId(), cleanedName)
        if (writeState(state.copy(collections = state.collections + collection))) collection else null
    }

    fun renameCollection(collectionId: String, name: String): Boolean {
        val cleanedName = validName(name) ?: return false
        return mutate { state ->
            if (state.collections.none { it.id == collectionId } || duplicateName(state, cleanedName, collectionId)) null
            else state.copy(collections = state.collections.map { if (it.id == collectionId) it.copy(name = cleanedName) else it })
        }
    }

    fun deleteCollection(collectionId: String): Boolean = mutate { state ->
        if (state.collections.none { it.id == collectionId }) null
        else state.copy(collections = state.collections.filterNot { it.id == collectionId })
    }

    fun setMembership(presetId: String, collectionId: String, included: Boolean): Boolean {
        if (!PresetIdentity.isValid(presetId)) return false
        return mutate { state ->
            if (state.collections.none { it.id == collectionId }) null
            else state.copy(collections = state.collections.map { collection ->
                if (collection.id != collectionId) collection else collection.copy(
                    presetIds = if (included) collection.presetIds + presetId else collection.presetIds - presetId
                )
            })
        }
    }

    /** Apply the complete membership dialog in one transaction. Unknown collection IDs fail closed. */
    fun setCollectionsForPreset(presetId: String, collectionIds: Set<String>): Boolean {
        if (!PresetIdentity.isValid(presetId)) return false
        return mutate { state ->
            if (!state.collections.map { it.id }.toSet().containsAll(collectionIds)) null
            else state.copy(collections = state.collections.map { collection ->
                collection.copy(presetIds = if (collection.id in collectionIds) collection.presetIds + presetId
                    else collection.presetIds - presetId)
            })
        }
    }

    /** Call only for an intentional deletion. Missing plugin/import IDs are otherwise retained. */
    fun removePreset(presetId: String): Boolean = removePresets(setOf(presetId))

    fun removePresets(presetIds: Set<String>): Boolean {
        if (presetIds.any { !PresetIdentity.isValid(it) }) return false
        return mutate { state -> state.copy(favorites = state.favorites - presetIds,
            collections = state.collections.map { it.copy(presetIds = it.presetIds - presetIds) }) }
    }

    /** Filtering and optional favourite ordering never alter controller indices or stored order. */
    fun filter(
        presets: List<LedPreset>,
        query: String = "",
        favoritesOnly: Boolean = false,
        collectionId: String? = null,
        favoritesFirst: Boolean = false
    ): List<IndexedPreset> {
        val state = state()
        val members = if (collectionId == null) null else
            state.collections.firstOrNull { it.id == collectionId }?.presetIds ?: emptySet()
        val filtered = PresetLibrarySearch.matchingIndices(presets, query).map { IndexedPreset(it, presets[it]) }
            .filter { (!favoritesOnly || it.preset.id in state.favorites) && (members == null || it.preset.id in members) }
        return if (favoritesFirst) filtered.sortedBy { if (it.preset.id in state.favorites) 0 else 1 } else filtered
    }

    private fun mutate(transform: (LibraryState) -> LibraryState?): Boolean = synchronized(prefs) {
        val current = readState() ?: return@synchronized false
        val updated = transform(current) ?: return@synchronized false
        if (updated == current) return@synchronized true
        writeState(updated)
    }

    private fun readState(): LibraryState? {
        val value = try { prefs.getString(PREF_KEY_LIBRARY, null) } catch (_: ClassCastException) { INVALID_TYPE }
        if (value == cachedRaw) return cachedState
        cachedRaw = value
        cachedState = when (value) {
            null -> LibraryState()
            is String -> decode(value)
            else -> null
        }
        return cachedState
    }

    private fun writeState(state: LibraryState): Boolean {
        if (!validState(state)) return false
        val previous = (prefs.all[PREF_KEY_LIBRARY] as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val root = previous ?: JSONObject()
        val previousCollections = root.optJSONArray("collections")
        val previousById = mutableMapOf<String, JSONObject>()
        if (previousCollections != null) for (index in 0 until previousCollections.length()) {
            previousCollections.optJSONObject(index)?.let { previousById[it.optString("id")] = it }
        }
        root.put("schemaVersion", SCHEMA_VERSION).put("favorites", JSONArray(state.favorites.sorted()))
        root.put("collections", JSONArray(state.collections.map { collection ->
            (previousById[collection.id] ?: JSONObject()).put("id", collection.id).put("name", collection.name)
                .put("presetIds", JSONArray(collection.presetIds.sorted()))
        }))
        val raw = root.toString()
        if (raw.length > MAX_JSON_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return false
        return prefs.edit().putString(PREF_KEY_LIBRARY, raw).commit()
    }

    private fun duplicateName(state: LibraryState, name: String, excludedId: String? = null): Boolean =
        state.collections.any { it.id != excludedId && it.name.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT) }

    companion object {
        private val UNREAD = Any()
        private val INVALID_TYPE = Any()
        const val SCHEMA_VERSION = 1
        const val PREF_KEY_LIBRARY = "preset_library_json"
        const val MAX_COLLECTIONS = 50
        const val MAX_IDS = 2_000
        const val MAX_NAME_LENGTH = 80
        const val MAX_JSON_BYTES = 1_048_576
        val BACKUP_PREF_KEYS = setOf(PREF_KEY_LIBRARY)

        internal fun decode(raw: String): LibraryState? {
            if (raw.length > MAX_JSON_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return null
            return runCatching {
                val root = JSONObject(raw)
                if (root.opt("schemaVersion") != SCHEMA_VERSION) return null
                val favorites = parseIds(root.optJSONArray("favorites") ?: return null) ?: return null
                val array = root.optJSONArray("collections") ?: return null
                if (array.length() > MAX_COLLECTIONS) return null
                val collections = (0 until array.length()).map { index ->
                    val obj = array.optJSONObject(index) ?: return null
                    val id = obj.opt("id") as? String ?: return null
                    val name = obj.opt("name") as? String ?: return null
                    val ids = parseIds(obj.optJSONArray("presetIds") ?: return null) ?: return null
                    Collection(id, name, ids)
                }
                LibraryState(favorites, collections).takeIf(::validState)
            }.getOrNull()
        }

        private fun validName(name: String): String? = name.trim().takeIf {
            it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && it.none(Char::isISOControl)
        }

        private fun validState(state: LibraryState): Boolean =
            state.favorites.size <= MAX_IDS && state.favorites.all(PresetIdentity::isValid) &&
                state.collections.size <= MAX_COLLECTIONS && state.collections.map { it.id }.distinct().size == state.collections.size &&
                state.collections.map { it.name.lowercase(Locale.ROOT) }.distinct().size == state.collections.size &&
                state.collections.all { PresetIdentity.isValid(it.id) && validName(it.name) == it.name &&
                    it.presetIds.size <= MAX_IDS && it.presetIds.all(PresetIdentity::isValid) }

        private fun parseIds(array: JSONArray): Set<String>? {
            if (array.length() > MAX_IDS) return null
            val ids = (0 until array.length()).map { array.opt(it) as? String ?: return null }
            if (ids.distinct().size != ids.size || ids.any { !PresetIdentity.isValid(it) }) return null
            return ids.toSet()
        }
    }
}

package io.github.tufein.duofrost

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One bounded undo. It restores exact raw data only while the post-action library is unchanged. */
class PresetUndoStore(
    private val prefs: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    enum class Action { DELETE, IMPORT }
    enum class RecordResult { RECORDED, NOTHING_CHANGED, INVALID_SNAPSHOT, TOO_LARGE, CONFLICT, WRITE_FAILED }
    enum class RestoreResult { RESTORED, NOTHING_PENDING, EXPIRED, CONFLICT, INVALID_SNAPSHOT, WRITE_FAILED }
    data class Snapshot(val presetsJson: String?, val libraryJson: String?, val mappingsJson: String? = null)
    data class PendingUndo(val action: Action, val expiresAtMillis: Long)
    private data class Record(val action: Action, val created: Long, val expires: Long, val before: Snapshot, val after: Snapshot)

    fun captureSnapshot(): Snapshot? = synchronized(prefs) { currentSnapshot()?.takeIf(::validSnapshot) }

    /** Capture before the mutation, then record immediately after its preset and metadata writes. */
    fun record(action: Action, before: Snapshot, after: Snapshot? = captureSnapshot()): RecordResult = synchronized(prefs) {
        if (after == null || !validSnapshot(before) || !validSnapshot(after)) return@synchronized RecordResult.INVALID_SNAPSHOT
        if (before == after) return@synchronized RecordResult.NOTHING_CHANGED
        if (currentSnapshot() != after) return@synchronized RecordResult.CONFLICT
        if (snapshotBytes(before) + snapshotBytes(after) > MAX_SNAPSHOT_BYTES) return@synchronized RecordResult.TOO_LARGE
        val now = nowMillis()
        if (now < 0 || now > Long.MAX_VALUE - TTL_MILLIS) return@synchronized RecordResult.INVALID_SNAPSHOT
        val raw = encode(Record(action, now, now + TTL_MILLIS, before, after))
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_RECORD_BYTES) return@synchronized RecordResult.TOO_LARGE
        if (prefs.edit().putString(PREF_KEY_UNDO, raw).commit()) RecordResult.RECORDED else RecordResult.WRITE_FAILED
    }

    /** Expired, damaged or superseded records are deliberately not offered in the UI. */
    fun pending(): PendingUndo? = synchronized(prefs) {
        val record = readRecord() ?: return@synchronized null
        if (!isCurrent(record)) return@synchronized null
        PendingUndo(record.action, record.expires)
    }

    fun restore(): RestoreResult = synchronized(prefs) {
        if (!prefs.contains(PREF_KEY_UNDO)) return@synchronized RestoreResult.NOTHING_PENDING
        val record = readRecord() ?: return@synchronized RestoreResult.INVALID_SNAPSHOT
        val now = nowMillis()
        if (now < record.created || now >= record.expires) return@synchronized RestoreResult.EXPIRED
        if (currentSnapshot() != record.after) return@synchronized RestoreResult.CONFLICT
        // One editor commits the preset, metadata and mapping documents and consumes the record.
        val editor = prefs.edit()
        putNullable(editor, PREF_KEY_PRESETS, record.before.presetsJson)
        putNullable(editor, PresetLibraryStore.PREF_KEY_LIBRARY, record.before.libraryJson)
        putNullable(editor, PREF_KEY_MAPPINGS, record.before.mappingsJson)
        editor.remove(PREF_KEY_UNDO)
        if (editor.commit()) RestoreResult.RESTORED else RestoreResult.WRITE_FAILED
    }

    fun clear(): Boolean = synchronized(prefs) { prefs.edit().remove(PREF_KEY_UNDO).commit() }

    /** Deleting artwork before this record expires would make the restored JSON reference a missing file. */
    fun retainedArtworkFileNames(): Set<String> = synchronized(prefs) {
        val record = readRecord() ?: return@synchronized emptySet()
        if (!isCurrent(record)) return@synchronized emptySet()
        val presets = record.before.presetsJson?.let(::JSONArray) ?: return@synchronized emptySet()
        buildSet {
            for (index in 0 until presets.length()) {
                (presets.optJSONObject(index)?.opt("customImageFileName") as? String)
                    ?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun currentSnapshot(): Snapshot? {
        val values = prefs.all
        if (values[PREF_KEY_PRESETS] != null && values[PREF_KEY_PRESETS] !is String) return null
        if (values[PresetLibraryStore.PREF_KEY_LIBRARY] != null && values[PresetLibraryStore.PREF_KEY_LIBRARY] !is String) return null
        if (values[PREF_KEY_MAPPINGS] != null && values[PREF_KEY_MAPPINGS] !is String) return null
        return Snapshot(values[PREF_KEY_PRESETS] as? String, values[PresetLibraryStore.PREF_KEY_LIBRARY] as? String,
            values[PREF_KEY_MAPPINGS] as? String)
    }

    private fun isCurrent(record: Record): Boolean {
        val now = nowMillis()
        return now >= record.created && now < record.expires && currentSnapshot() == record.after
    }

    private fun readRecord(): Record? {
        val raw = prefs.all[PREF_KEY_UNDO] as? String ?: return null
        if (raw.length > MAX_RECORD_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_RECORD_BYTES) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.opt("schemaVersion") != SCHEMA_VERSION) return null
            val actionName = root.opt("action") as? String ?: return null
            val action = Action.entries.firstOrNull { it.name == actionName } ?: return null
            val created = strictLong(root.opt("createdAtMillis")) ?: return null
            val expires = strictLong(root.opt("expiresAtMillis")) ?: return null
            if (created < 0 || created > Long.MAX_VALUE - TTL_MILLIS || expires != created + TTL_MILLIS) return null
            val before = parseSnapshot(root.optJSONObject("before") ?: return null) ?: return null
            val after = parseSnapshot(root.optJSONObject("after") ?: return null) ?: return null
            if (!validSnapshot(before) || !validSnapshot(after) || before == after ||
                snapshotBytes(before) + snapshotBytes(after) > MAX_SNAPSHOT_BYTES) return null
            Record(action, created, expires, before, after)
        }.getOrNull()
    }

    private fun encode(record: Record): String = JSONObject().put("schemaVersion", SCHEMA_VERSION)
        .put("action", record.action.name).put("createdAtMillis", record.created).put("expiresAtMillis", record.expires)
        .put("before", snapshotJson(record.before)).put("after", snapshotJson(record.after)).toString()

    private fun snapshotJson(snapshot: Snapshot): JSONObject = JSONObject()
        .put("presetsJson", snapshot.presetsJson ?: JSONObject.NULL)
        .put("libraryJson", snapshot.libraryJson ?: JSONObject.NULL)
        .put("mappingsJson", snapshot.mappingsJson ?: JSONObject.NULL)

    private fun parseSnapshot(obj: JSONObject): Snapshot? {
        if (!obj.has("presetsJson") || !obj.has("libraryJson") || !obj.has("mappingsJson")) return null
        if (!obj.isNull("presetsJson") && obj.opt("presetsJson") !is String) return null
        if (!obj.isNull("libraryJson") && obj.opt("libraryJson") !is String) return null
        if (!obj.isNull("mappingsJson") && obj.opt("mappingsJson") !is String) return null
        return Snapshot(obj.opt("presetsJson") as? String, obj.opt("libraryJson") as? String,
            obj.opt("mappingsJson") as? String)
    }

    private fun validSnapshot(snapshot: Snapshot): Boolean {
        if ((snapshot.presetsJson?.length ?: 0) > MAX_SNAPSHOT_BYTES ||
            (snapshot.libraryJson?.length ?: 0) > MAX_SNAPSHOT_BYTES ||
            (snapshot.mappingsJson?.length ?: 0) > MAX_SNAPSHOT_BYTES) return false
        if (snapshotBytes(snapshot) > MAX_SNAPSHOT_BYTES) return false
        val presets = snapshot.presetsJson
        if (presets != null && (runCatching { JSONArray(presets).length() }.getOrNull() ?: return false) > MAX_PRESETS) return false
        if (snapshot.libraryJson != null && PresetLibraryStore.decode(snapshot.libraryJson) == null) return false
        val mappings = snapshot.mappingsJson ?: return true
        return runCatching {
            val obj = JSONObject(mappings)
            obj.length() <= MAX_PRESETS && obj.keys().asSequence().all { key ->
                key.isNotBlank() && obj.opt(key) is String && (obj.opt(key) as String).isNotBlank()
            }
        }.getOrDefault(false)
    }

    private fun snapshotBytes(snapshot: Snapshot): Long = (snapshot.presetsJson?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) +
        (snapshot.libraryJson?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) +
        (snapshot.mappingsJson?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L)

    private fun putNullable(editor: SharedPreferences.Editor, key: String, value: String?) {
        if (value == null) editor.remove(key) else editor.putString(key, value)
    }

    private fun strictLong(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val PREF_KEY_PRESETS = "presets_json"
        const val PREF_KEY_MAPPINGS = "app_profile_mappings"
        const val PREF_KEY_UNDO = "preset_library_undo_json"
        const val TTL_MILLIS = 10 * 60_000L
        const val MAX_PRESETS = 2_000
        const val MAX_SNAPSHOT_BYTES = 1_048_576
        const val MAX_RECORD_BYTES = 2_097_152
        /** Local recovery state is never exported or imported in a backup. */
        val TRANSIENT_PREF_KEYS = setOf(PREF_KEY_UNDO)
    }
}

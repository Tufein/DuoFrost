package io.github.tufein.duofrost.scenes

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class SceneStoreTest {
    private val data = mutableMapOf<String, Any?>()
    private val prefs = memoryPreferences(data)
    private val store = SceneStore(prefs)
    private val rule = SceneRule("retro", "Retro", presetId = "p-1", target = SceneTarget.GROUP, groupId = "games",
        startMinute = 22 * 60, endMinute = 7 * 60, daysOfWeek = setOf(5, 6), maxBatteryPercent = 30,
        charging = ChargingCondition.ON_BATTERY, maxBrightnessPercent = 40, priority = 10)
    private val group = AppGroup("games", "Games", setOf("org.example.game", "org.example.emulator"))

    @Test fun configurationRoundTripPreservesEveryCondition() {
        assertTrue(store.saveRules(listOf(rule)))
        assertTrue(store.saveGroups(listOf(group)))
        assertEquals(listOf(rule), store.loadRules())
        assertEquals(listOf(group), store.loadGroups())
        assertEquals(1, JSONObject(data[SceneStore.PREF_KEY_RULES] as String).getInt("schemaVersion"))
    }

    @Test fun configurationIsDisabledByDefaultAndCanBeEnabledExplicitly() {
        assertFalse(store.isEnabled)
        store.isEnabled = true
        assertTrue(store.isEnabled)
    }

    @Test fun cacheRechecksChangedPreferencesAndExternalDeletion() {
        store.saveRules(listOf(rule))
        assertEquals(listOf(rule), store.loadRules())
        store.saveRules(listOf(rule.copy(name = "Changed")))
        assertEquals("Changed", store.loadRules().single().name)
        data.remove(SceneStore.PREF_KEY_RULES)
        assertTrue(store.loadRules().isEmpty())
    }

    @Test fun futureSchemaCannotRunOrBeOverwrittenByAnyEditorSave() {
        val future = """{"schemaVersion":2,"rules":[{"future":"keep me"}]}"""
        data[SceneStore.PREF_KEY_RULES] = future
        data[SceneStore.PREF_KEY_ENABLED] = true
        assertTrue(store.isReadOnly)
        assertFalse(store.isEnabled)
        assertTrue(store.loadRules().isEmpty())
        assertFalse(store.saveRules(emptyList()))
        assertFalse(store.saveGroups(listOf(group)))
        store.isEnabled = false
        assertEquals(future, data[SceneStore.PREF_KEY_RULES])
        assertEquals(true, data[SceneStore.PREF_KEY_ENABLED])
    }

    @Test fun malformedConditionsFailClosedInsteadOfBecomingUnconditional() {
        store.saveRules(listOf(rule))
        val original = data[SceneStore.PREF_KEY_RULES] as String
        val changes = listOf("daysOfWeek" to "[]", "daysOfWeek" to "[1,1]", "daysOfWeek" to "[1.5]",
            "startMinute" to "\"1320\"", "maxBatteryPercent" to "\"30\"", "enabled" to "\"true\"",
            "charging" to "\"UNKNOWN\"", "target" to "\"UNKNOWN\"", "groupId" to "42")
        for ((key, value) in changes) {
            val root = JSONObject(original)
            val replacement = JSONObject("{\"value\":$value}").get("value")
            root.getJSONArray("rules").getJSONObject(0).put(key, replacement)
            data[SceneStore.PREF_KEY_RULES] = root.toString()
            assertTrue("Malformed $key=$value", store.loadRules().isEmpty())
        }
    }

    @Test fun invalidSavesLeaveExistingDataIntact() {
        store.saveRules(listOf(rule))
        store.saveGroups(listOf(group))
        val snapshot = data.toMap()
        assertFalse(store.saveRules(listOf(rule.copy(priority = 200))))
        assertFalse(store.saveRules(listOf(rule, rule)))
        assertFalse(store.saveGroups(listOf(group.copy(packages = setOf("bad package")))))
        assertFalse(store.saveGroups(List(101) { group.copy(id = "group-$it") }))
        assertEquals(snapshot, data)
    }

    @Test fun aggregateSizeLimitCannotWriteASnapshotThatTheReaderRejects() {
        store.saveGroups(listOf(group))
        val snapshot = data.toMap()
        val longPackages = (0 until 200).map { "org.example." + "a".repeat(230) + it }.toSet()
        val largeGroups = (0 until 100).map { AppGroup("group-$it", "Group $it", longPackages) }
        assertTrue(largeGroups.all { it.isValid() })
        assertFalse(store.saveGroups(largeGroups))
        assertEquals(snapshot, data)
        assertFalse(store.isReadOnly)
    }

    @Test fun futureGroupSchemaAlsoProtectsTheWholeSceneConfiguration() {
        data[SceneStore.PREF_KEY_GROUPS] = """{"schemaVersion":9,"groups":[]}"""
        assertTrue(store.isReadOnly)
        assertFalse(store.saveRules(listOf(rule)))
        assertTrue(store.loadGroups().isEmpty())
    }

    @Test fun malformedAndOverlargeSnapshotsNeverPartiallyExecute() {
        for (raw in listOf("not JSON", "{}", "[]", """{"schemaVersion":1,"rules":{}}""",
            """{"schemaVersion":1,"rules":[{"id":"broken"}]}""")) {
            data[SceneStore.PREF_KEY_RULES] = raw
            assertTrue(store.loadRules().isEmpty())
        }
        store.saveRules(listOf(rule))
        val root = JSONObject(data[SceneStore.PREF_KEY_RULES] as String)
        root.getJSONArray("rules").put(root.getJSONArray("rules").getJSONObject(0))
        data[SceneStore.PREF_KEY_RULES] = root.toString()
        assertTrue(store.loadRules().isEmpty())
    }

    @Test fun temporaryChoiceRetainsOriginalDeadlineAfterProcessRestore() {
        assertTrue(store.setTemporaryScene("preset", 30, 1000, 5))
        val restored = SceneStore(prefs).getTemporaryScene(60_000, 5)
        assertEquals(TemporaryScene("preset", 1_801_000, 5), restored)
        assertNotNull(store.getTemporaryScene(1_800_999, 5))
        assertNull(store.getTemporaryScene(1_801_000, 5))
    }

    @Test fun temporaryChoiceExpiresOnRebootOrMonotonicClockReset() {
        store.setTemporaryScene("preset", 30, 60_000, 5)
        assertNull(store.getTemporaryScene(60_001, 6))
        assertNull(store.getTemporaryScene(100, 5))
        assertNull(store.getTemporaryScene(60_001, -1))
    }

    @Test fun invalidTemporaryInputsCannotReplaceExistingChoice() {
        store.setTemporaryScene("preset", 30, 1000, 5)
        val snapshot = data.toMap()
        for (minutes in listOf(-1, 0, 1441, Int.MAX_VALUE))
            assertFalse(store.setTemporaryScene("other", minutes, 1000, 5))
        assertFalse(store.setTemporaryScene("", 1, 1000, 5))
        assertFalse(store.setTemporaryScene("other", 1, -1, 5))
        assertFalse(store.setTemporaryScene("other", 1, 1000, -1))
        assertFalse(store.setTemporaryScene("other", 1, Long.MAX_VALUE, 5))
        assertEquals(snapshot, data)
    }

    @Test fun corruptedTemporarySnapshotFailsClosed() {
        store.setTemporaryScene("preset", 30, 1000, 5)
        data[SceneStore.PREF_KEY_TEMPORARY_EXPIRES] = Long.MAX_VALUE
        assertNull(store.getTemporaryScene(1001, 5))
        data[SceneStore.PREF_KEY_TEMPORARY_EXPIRES] = 999L
        assertNull(store.getTemporaryScene(1001, 5))
        data[SceneStore.PREF_KEY_TEMPORARY_EXPIRES] = "1801000"
        assertNull(store.getTemporaryScene(1001, 5))
    }

    @Test fun temporaryStateIsExcludedFromBackupAndClearKeepsConfiguration() {
        store.saveRules(listOf(rule))
        store.isEnabled = true
        store.setTemporaryScene("preset", 30, 1000, 5)
        assertTrue(SceneStore.BACKUP_PREF_KEYS.intersect(SceneStore.TEMPORARY_PREF_KEYS).isEmpty())
        store.clearTemporaryScene()
        assertNull(store.getTemporaryScene(1001, 5))
        assertEquals(listOf(rule), store.loadRules())
        assertTrue(store.isEnabled)
    }

    private fun memoryPreferences(values: MutableMap<String, Any?>): SharedPreferences {
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0])
                "getString", "getStringSet", "getInt", "getLong", "getFloat", "getBoolean" ->
                    values[args!![0]] ?: args[1]
                "edit" -> memoryEditor(values)
                "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                else -> error("Unexpected preferences method ${method.name}")
            }
        } as SharedPreferences
    }

    private fun memoryEditor(values: MutableMap<String, Any?>): SharedPreferences.Editor {
        val updates = mutableMapOf<String, Any?>()
        var clear = false
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString", "putStringSet", "putInt", "putLong", "putFloat", "putBoolean" -> {
                    updates[args!![0] as String] = args[1]
                    editor
                }
                "remove" -> { updates[args!![0] as String] = null; editor }
                "clear" -> { clear = true; editor }
                "commit", "apply" -> {
                    if (clear) values.clear()
                    updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                    if (method.name == "commit") true else null
                }
                else -> error("Unexpected editor method ${method.name}")
            }
        } as SharedPreferences.Editor
        return editor
    }
}

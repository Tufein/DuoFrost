package io.github.tufein.duofrost.scenes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Versioned configuration; future formats stay intact and are never executed. */
class SceneStore(private val prefs: SharedPreferences) {
    private var cachedRulesRaw: String? = null
    private var cachedRules: List<SceneRule> = emptyList()
    private var cachedGroupsRaw: String? = null
    private var cachedGroups: List<AppGroup> = emptyList()

    var isEnabled: Boolean
        get() = !isReadOnly && runCatching { prefs.getBoolean(PREF_KEY_ENABLED, false) }.getOrDefault(false)
        set(value) {
            if (!isReadOnly) prefs.edit().putBoolean(PREF_KEY_ENABLED, value).apply()
        }

    val isReadOnly: Boolean
        get() = unsupportedSchema(readString(PREF_KEY_RULES)) || unsupportedSchema(readString(PREF_KEY_GROUPS))

    @Synchronized fun loadRules(): List<SceneRule> {
        val raw = readString(PREF_KEY_RULES)
        if (raw == cachedRulesRaw) return cachedRules.toList()
        cachedRulesRaw = raw
        cachedRules = readItems(raw, "rules", ::parseRule) ?: emptyList()
        return cachedRules.toList()
    }

    fun saveRules(rules: List<SceneRule>): Boolean {
        if (isReadOnly || rules.size > SceneLimits.MAX_ITEMS || rules.any { !it.isValid() } ||
            rules.map { it.id }.distinct().size != rules.size) return false
        val raw = envelope("rules", rules.map(::ruleJson))
        if (raw.length > SceneLimits.MAX_JSON_LENGTH) return false
        return prefs.edit().putString(PREF_KEY_RULES, raw).commit()
    }

    @Synchronized fun loadGroups(): List<AppGroup> {
        val raw = readString(PREF_KEY_GROUPS)
        if (raw == cachedGroupsRaw) return cachedGroups.toList()
        cachedGroupsRaw = raw
        cachedGroups = readItems(raw, "groups", ::parseGroup) ?: emptyList()
        return cachedGroups.toList()
    }

    fun saveGroups(groups: List<AppGroup>): Boolean {
        if (isReadOnly || groups.size > SceneLimits.MAX_ITEMS || groups.any { !it.isValid() } ||
            groups.map { it.id }.distinct().size != groups.size) return false
        val raw = envelope("groups", groups.map(::groupJson))
        if (raw.length > SceneLimits.MAX_JSON_LENGTH) return false
        return prefs.edit().putString(PREF_KEY_GROUPS, raw).commit()
    }

    fun setTemporaryScene(presetId: String, durationMinutes: Int, nowElapsed: Long, bootCount: Int): Boolean {
        if (isReadOnly || !SceneLimits.validId(presetId) || durationMinutes !in 1..SceneLimits.MAX_TEMPORARY_MINUTES ||
            nowElapsed < 0 || bootCount < 0) return false
        val duration = durationMinutes * 60_000L
        if (nowElapsed > Long.MAX_VALUE - duration) return false
        return prefs.edit().putString(PREF_KEY_TEMPORARY_PRESET, presetId)
            .putLong(PREF_KEY_TEMPORARY_CREATED, nowElapsed)
            .putLong(PREF_KEY_TEMPORARY_EXPIRES, nowElapsed + duration)
            .putInt(PREF_KEY_TEMPORARY_BOOT, bootCount).commit()
    }

    fun getTemporaryScene(nowElapsed: Long, bootCount: Int): TemporaryScene? {
        if (isReadOnly || nowElapsed < 0 || bootCount < 0) return null
        val values = prefs.all
        val presetId = values[PREF_KEY_TEMPORARY_PRESET] as? String ?: return null
        val created = values[PREF_KEY_TEMPORARY_CREATED] as? Long ?: return null
        val expires = values[PREF_KEY_TEMPORARY_EXPIRES] as? Long ?: return null
        val savedBoot = values[PREF_KEY_TEMPORARY_BOOT] as? Int ?: return null
        if (!SceneLimits.validId(presetId) || savedBoot != bootCount || created < 0 || nowElapsed < created ||
            expires <= created || expires - created > SceneLimits.MAX_TEMPORARY_MINUTES * 60_000L ||
            nowElapsed >= expires) return null
        return TemporaryScene(presetId, expires, savedBoot)
    }

    fun clearTemporaryScene(): Boolean {
        val editor = prefs.edit()
        TEMPORARY_PREF_KEYS.forEach(editor::remove)
        // Stop and Resume are rare explicit actions. Complete the deletion
        // before process death can restore an old hold on the next Start.
        return editor.commit()
    }

    private fun readString(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    private fun unsupportedSchema(raw: String?): Boolean {
        if (raw == null) return false
        if (raw.length > SceneLimits.MAX_JSON_LENGTH) return true
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        return strictInt(root.opt("schemaVersion")) != SCHEMA_VERSION
    }

    private fun <T> readItems(raw: String?, key: String, parse: (JSONObject) -> T?): List<T>? {
        if (raw == null || raw.length > SceneLimits.MAX_JSON_LENGTH) return null
        return runCatching {
            val root = JSONObject(raw)
            if (strictInt(root.opt("schemaVersion")) != SCHEMA_VERSION) return null
            val items = root.optJSONArray(key) ?: return null
            if (items.length() > SceneLimits.MAX_ITEMS) return null
            val result = ArrayList<T>(items.length())
            val ids = mutableSetOf<String>()
            for (index in 0 until items.length()) {
                val obj = items.optJSONObject(index) ?: return null
                val id = obj.opt("id") as? String ?: return null
                if (!ids.add(id)) return null
                result += parse(obj) ?: return null
            }
            result
        }.getOrNull()
    }

    private fun parseRule(obj: JSONObject): SceneRule? {
        // Optional nulls are distinct from malformed types: a damaged group or
        // battery condition must never turn into a broader, unconditional rule.
        val stringFields = listOf("presetId", "groupId")
        if (stringFields.any { obj.has(it) && !obj.isNull(it) && obj.opt(it) !is String }) return null
        val intFields = listOf("startMinute", "endMinute", "maxBatteryPercent", "maxBrightnessPercent")
        if (intFields.any { obj.has(it) && !obj.isNull(it) && strictInt(obj.opt(it)) == null }) return null
        val id = obj.opt("id") as? String ?: return null
        val name = obj.opt("name") as? String ?: return null
        val enabled = if (obj.has("enabled")) obj.opt("enabled") as? Boolean ?: return null else true
        val target = enumValue<SceneTarget>(obj, "target", SceneTarget.ANY) ?: return null
        val charging = enumValue<ChargingCondition>(obj, "charging", ChargingCondition.ANY) ?: return null
        val priority = if (obj.has("priority")) strictInt(obj.opt("priority")) ?: return null else 0
        val days = if (!obj.has("daysOfWeek")) (1..7).toSet() else {
            val array = obj.optJSONArray("daysOfWeek") ?: return null
            if (array.length() !in 1..7) return null
            val parsed = (0 until array.length()).map { strictInt(array.opt(it)) ?: return null }
            if (parsed.distinct().size != parsed.size) return null
            parsed.toSet()
        }
        return SceneRule(
            id = id,
            name = name,
            enabled = enabled,
            presetId = obj.opt("presetId") as? String,
            target = target,
            groupId = obj.opt("groupId") as? String,
            startMinute = strictInt(obj.opt("startMinute")),
            endMinute = strictInt(obj.opt("endMinute")),
            daysOfWeek = days,
            maxBatteryPercent = strictInt(obj.opt("maxBatteryPercent")),
            charging = charging,
            maxBrightnessPercent = strictInt(obj.opt("maxBrightnessPercent")),
            priority = priority
        ).takeIf { it.isValid() }
    }

    private fun parseGroup(obj: JSONObject): AppGroup? {
        val id = obj.opt("id") as? String ?: return null
        val name = obj.opt("name") as? String ?: return null
        val array = obj.optJSONArray("packages") ?: return null
        if (array.length() !in 1..SceneLimits.MAX_PACKAGES_PER_GROUP) return null
        val packages = (0 until array.length()).map { array.opt(it) as? String ?: return null }
        if (packages.distinct().size != packages.size) return null
        return AppGroup(id, name, packages.toSet()).takeIf { it.isValid() }
    }

    private inline fun <reified T : Enum<T>> enumValue(obj: JSONObject, key: String, default: T): T? {
        if (!obj.has(key)) return default
        val value = obj.opt(key) as? String ?: return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private fun strictInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
        else -> null
    }

    private fun envelope(key: String, items: List<JSONObject>): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION).put(key, JSONArray(items)).toString()

    private fun ruleJson(rule: SceneRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("name", rule.name)
        put("enabled", rule.enabled)
        put("target", rule.target.name)
        put("charging", rule.charging.name)
        put("priority", rule.priority)
        put("daysOfWeek", JSONArray(rule.daysOfWeek.sorted()))
        rule.presetId?.let { put("presetId", it) }
        rule.groupId?.let { put("groupId", it) }
        rule.startMinute?.let { put("startMinute", it) }
        rule.endMinute?.let { put("endMinute", it) }
        rule.maxBatteryPercent?.let { put("maxBatteryPercent", it) }
        rule.maxBrightnessPercent?.let { put("maxBrightnessPercent", it) }
    }

    private fun groupJson(group: AppGroup): JSONObject = JSONObject().put("id", group.id).put("name", group.name)
        .put("packages", JSONArray(group.packages.sorted()))

    companion object {
        const val SCHEMA_VERSION = 1
        const val PREF_KEY_RULES = "scenes_rules_json"
        const val PREF_KEY_GROUPS = "scenes_groups_json"
        const val PREF_KEY_ENABLED = "smart_scenes_enabled"
        const val PREF_KEY_TEMPORARY_PRESET = "scenes_temporary_preset"
        const val PREF_KEY_TEMPORARY_CREATED = "scenes_temporary_created"
        const val PREF_KEY_TEMPORARY_EXPIRES = "scenes_temporary_expires"
        const val PREF_KEY_TEMPORARY_BOOT = "scenes_temporary_boot"
        val BACKUP_PREF_KEYS = setOf(PREF_KEY_RULES, PREF_KEY_GROUPS, PREF_KEY_ENABLED)
        val TEMPORARY_PREF_KEYS = setOf(PREF_KEY_TEMPORARY_PRESET, PREF_KEY_TEMPORARY_CREATED,
            PREF_KEY_TEMPORARY_EXPIRES, PREF_KEY_TEMPORARY_BOOT)
    }
}

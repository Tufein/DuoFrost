package io.github.tufein.duofrost.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetRepository
import org.json.JSONObject

class AppProfileManager(
    private val prefs: SharedPreferences,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis
) {

    data class SwitchResult(
        val presetName: String?,
        val preset: LedPreset?
    )

    companion object {
        private const val TAG = "BIBI"
        private const val PREF_KEY_MAPPINGS = "app_profile_mappings"
        private const val PREF_KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val PREF_KEY_GAME_SCENE_ENABLED = "game_scene_enabled"
        private const val PREF_KEY_GAME_SCENE_PRESET = "game_scene_preset"
        private const val PREF_KEY_PENDING_PROJECTION_PACKAGE = "pending_projection_package"
        private const val PREF_KEY_PENDING_PROJECTION_PRESET = "pending_projection_preset"
        private const val PREF_KEY_PENDING_PROJECTION_NOTIFIED = "pending_projection_notified"
        private const val FOREGROUND_QUERY_WINDOW_MS = 2500L
        private const val FOREGROUND_QUERY_CACHE_MS = 350L
        private const val HOME_PACKAGES_CACHE_MS = 10_000L
        private const val APPLICATION_CATEGORY_GAME = 0
        private const val INTENT_CATEGORY_GAME = "android.intent.category.GAME"
        private val UNREAD_MAPPINGS = Any()
    }

    @Volatile
    private var lastResolvedPresetName: String? = null
    @Volatile
    private var hasResolvedPresetOnce: Boolean = false
    private var cachedMappingsStored: Any? = UNREAD_MAPPINGS
    private var cachedMappings: Map<String, String> = emptyMap()
    private var cachedMappingsEditable = true
    private var lastForegroundQueryAt: Long = -1L
    private var cachedForegroundPackage: String? = null
    private var lastHomePackagesQueryAt: Long = -1L
    private var cachedHomePackages: Set<String> = emptySet()
    private var lastGamePackageQueryAt: Long = -1L
    private var cachedGamePackageName: String? = null
    private var cachedGamePackageResult: Boolean = false
    private val presetRepository = PresetRepository(prefs)

    var isEnabled: Boolean
        get() = prefs.getBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, value).apply()

    var isGameSceneEnabled: Boolean
        get() = prefs.getBoolean(PREF_KEY_GAME_SCENE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_GAME_SCENE_ENABLED, value).apply()

    val gameScenePresetName: String?
        get() = prefs.getString(PREF_KEY_GAME_SCENE_PRESET, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun setGameScenePreset(name: String?) {
        prefs.edit().apply {
            if (name.isNullOrBlank()) remove(PREF_KEY_GAME_SCENE_PRESET)
            else putString(PREF_KEY_GAME_SCENE_PRESET, name.trim())
        }.apply()
    }

    fun renameGameScenePreset(oldName: String, newName: String) {
        if (gameScenePresetName == oldName && oldName != newName) {
            setGameScenePreset(newName)
        }
    }

    fun clearGameSceneIfMissing(validPresetNames: Collection<String>) {
        val current = gameScenePresetName ?: return
        if (current !in validPresetNames) {
            setGameScenePreset(null)
            isGameSceneEnabled = false
        }
    }

    /** Damaged or newer mapping data can be read safely, but must never be overwritten. */
    val canEditMappings: Boolean
        get() = synchronized(prefs) {
            getMappings()
            cachedMappingsEditable
        }

    fun getMappings(): Map<String, String> = synchronized(prefs) {
        val stored = prefs.all[PREF_KEY_MAPPINGS]
        if (stored == cachedMappingsStored) return@synchronized cachedMappings
        val parsed = when (stored) {
            null -> emptyMap()
            is String -> parseMappings(stored)
            else -> null
        }
        cachedMappingsStored = stored
        cachedMappingsEditable = parsed != null
        cachedMappings = parsed ?: emptyMap()
        cachedMappings
    }

    private fun parseMappings(raw: String): Map<String, String>? = runCatching {
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { key ->
                val value = obj.opt(key) as? String ?: return null
                if (key.isBlank() || value.isBlank()) return null
                put(key.trim(), value.trim())
            }
        }
    }.getOrNull()

    fun setMapping(packageName: String, presetName: String) {
        synchronized(prefs) {
            if (packageName.isBlank() || presetName.isBlank() || !canEditMappings) return@synchronized
            val mappings = getMappings().toMutableMap()
            mappings[packageName.trim()] = presetName.trim()
            saveMappings(mappings)
        }
    }

    fun removeMapping(packageName: String) {
        synchronized(prefs) {
            if (!canEditMappings) return@synchronized
            val mappings = getMappings().toMutableMap()
            mappings.remove(packageName)
            saveMappings(mappings)
        }
    }

    fun replaceMappings(mappings: Map<String, String>) {
        synchronized(prefs) { saveMappings(mappings) }
    }

    fun renamePresetInMappings(oldName: String, newName: String) {
        synchronized(prefs) {
            if (oldName == newName || !canEditMappings) return@synchronized
            val updated = getMappings().mapValues { (_, value) ->
                if (value == oldName) newName else value
            }
            saveMappings(updated)
        }
    }

    fun removeMappingsReferencing(presetNames: Collection<String>) {
        synchronized(prefs) {
            if (presetNames.isEmpty() || !canEditMappings) return@synchronized
            val nameSet = presetNames.toHashSet()
            val current = getMappings()
            val filtered = current.filterValues { it !in nameSet }
            if (filtered.size == current.size) return@synchronized
            saveMappings(filtered)
        }
    }

    private fun saveMappings(mappings: Map<String, String>) {
        if (!canEditMappings || mappings.any { (key, value) -> key.isBlank() || value.isBlank() }) return
        val obj = JSONObject()
        mappings.forEach { (k, v) -> obj.put(k, v) }
        val raw = obj.toString()
        prefs.edit().putString(PREF_KEY_MAPPINGS, raw).apply()
        cachedMappingsStored = raw
        cachedMappingsEditable = true
        cachedMappings = mappings.toMap()
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null

        val nowElapsed = elapsedRealtime()
        if (isCacheFresh(lastForegroundQueryAt, nowElapsed, FOREGROUND_QUERY_CACHE_MS)) {
            return cachedForegroundPackage
        }
        // UsageEvents timestamps use wall time; cache ages use elapsed time so
        // changing the clock cannot pin a stale foreground app in the cache.
        val now = wallTimeMillis()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // UsageEvents is the most reactive & accurate source for foreground detection.
        val latestFromEvents = resolveForegroundFromEvents(usm, now)
        if (!latestFromEvents.isNullOrBlank()) {
            lastForegroundQueryAt = nowElapsed
            cachedForegroundPackage = latestFromEvents
            Log.d(TAG, "getForegroundPackage: resolved from events → '$latestFromEvents'")
            return latestFromEvents
        }

        // No recent foreground events (e.g. user has been on home screen for a while
        // and the events window has scrolled past).  If we already have a cached
        // result (from a previous events-based detection), keep using it – falling
        // back to queryUsageStats here would return the *most recently used app*
        // (by lastTimeUsed), which is stale and wrong (e.g. it would return app A
        // even though the user is on the home screen).
        if (cachedForegroundPackage != null) {
            lastForegroundQueryAt = nowElapsed
            Log.d(TAG, "getForegroundPackage: no recent events, keeping cached → '$cachedForegroundPackage'")
            return cachedForegroundPackage
        }

        // First-time bootstrap only: no events and no cache yet.
        // Use queryUsageStats as a last resort to seed the initial value.
        val stats = runCatching {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - FOREGROUND_QUERY_WINDOW_MS,
                now
            )
        }.getOrNull()

        lastForegroundQueryAt = nowElapsed
        if (stats.isNullOrEmpty()) {
            cachedForegroundPackage = null
            Log.d(TAG, "getForegroundPackage: bootstrap — no usage stats → null")
            return null
        }
        val latest = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        cachedForegroundPackage = latest
        Log.d(TAG, "getForegroundPackage: bootstrap from stats → '$latest'")
        return latest
    }

    /** Read the legacy profile policy without mutating its switch deduplication. */
    fun resolveSelection(context: Context, currentPackage: String?): AppProfileSelection.Result? {
        if (!isEnabled || !hasUsageStatsPermission(context) || currentPackage.isNullOrBlank()) return null
        val homePackages = getHomePackages(context)
        return AppProfileSelection.resolve(
            currentPackage = currentPackage,
            selfPackage = context.packageName,
            homePackages = homePackages,
            mappings = getMappings(),
            gameSceneEnabled = isGameSceneEnabled,
            gameScenePresetName = gameScenePresetName,
            isGamePackage = currentPackage != context.packageName &&
                currentPackage !in homePackages && isGamePackage(context, currentPackage),
            fallbackPresetName = resolveDefaultPresetName()
        )
    }

    fun isHomePackage(context: Context, packageName: String?): Boolean =
        packageName == context.packageName || packageName in getHomePackages(context)

    private fun resolveForegroundFromEvents(
        usageStatsManager: UsageStatsManager,
        now: Long
    ): String? {
        val events = runCatching {
            usageStatsManager.queryEvents(now - FOREGROUND_QUERY_WINDOW_MS, now)
        }.getOrNull() ?: return null

        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            if (!isForegroundEvent) continue

            val packageName = event.packageName ?: continue
            if (event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = packageName
            }
        }

        return latestPackage
    }

    /**
     * Resolves the effective app-profile preset from current foreground package.
     * Returns null when no effective change happened since the previous check.
     */
    fun checkForSwitch(context: Context): SwitchResult? {
        if (!isEnabled) {
            Log.d(TAG, "checkForSwitch: app profile disabled, returning null")
            return null
        }
        if (!hasUsageStatsPermission(context)) {
            Log.d(TAG, "checkForSwitch: no usage stats permission, returning null")
            return null
        }

        val currentPackage = getForegroundPackage(context)
        Log.d(TAG, "checkForSwitch: currentPackage=$currentPackage, lastResolvedPresetName=$lastResolvedPresetName, hasResolvedPresetOnce=$hasResolvedPresetOnce")

        val mappings = getMappings()
        val fallbackPresetName = resolveDefaultPresetName()
        Log.d(TAG, "checkForSwitch: fallbackPresetName=$fallbackPresetName, mappingsCount=${mappings.size}")

        // If the foreground package is null/blank, this is a transient detection failure.
        // Do NOT switch away from the current preset.
        if (currentPackage.isNullOrBlank()) {
            Log.d(TAG, "checkForSwitch: foreground is null/blank → transient, keeping current preset (no switch)")
            return null
        }

        val homePackages = getHomePackages(context)
        val isGamePackage = currentPackage != context.packageName &&
            currentPackage !in homePackages &&
            isGamePackage(context, currentPackage)
        val selection = AppProfileSelection.resolve(
            currentPackage = currentPackage,
            selfPackage = context.packageName,
            homePackages = homePackages,
            mappings = mappings,
            gameSceneEnabled = isGameSceneEnabled,
            gameScenePresetName = gameScenePresetName,
            isGamePackage = isGamePackage,
            fallbackPresetName = fallbackPresetName
        )
        val presetName = selection.presetName
        Log.d(TAG, "checkForSwitch: resolved presetName='$presetName', source=${selection.source}, gamePackage=$isGamePackage")

        if (hasResolvedPresetOnce && presetName == lastResolvedPresetName) {
            Log.d(TAG, "checkForSwitch: same preset as before, returning null (no change)")
            return null
        }
        lastResolvedPresetName = presetName
        hasResolvedPresetOnce = true

        val preset = presetName?.let { loadPresetByName(it) }
        Log.d(TAG, "checkForSwitch: SWITCHING → presetName='$presetName', preset animationType=${preset?.animationType}, presetIsNull=${preset == null}")
        return SwitchResult(presetName = presetName, preset = preset)
    }

    fun resetLastForegroundPackage() {
        Log.d(TAG, "resetLastForegroundPackage: clearing all tracking state")
        lastResolvedPresetName = null
        hasResolvedPresetOnce = false
        cachedForegroundPackage = null
        lastForegroundQueryAt = -1L
        lastHomePackagesQueryAt = -1L
        cachedHomePackages = emptySet()
        lastGamePackageQueryAt = -1L
        cachedGamePackageName = null
    }

    private fun getHomePackages(context: Context): Set<String> {
        val now = elapsedRealtime()
        if (!isCacheFresh(lastHomePackagesQueryAt, now, HOME_PACKAGES_CACHE_MS) || cachedHomePackages.isEmpty()) {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            cachedHomePackages = runCatching {
                context.packageManager.queryIntentActivities(homeIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
            lastHomePackagesQueryAt = now
        }
        return cachedHomePackages
    }

    fun isGamePackage(context: Context, packageName: String): Boolean {
        val now = elapsedRealtime()
        if (packageName == cachedGamePackageName &&
            isCacheFresh(lastGamePackageQueryAt, now, HOME_PACKAGES_CACHE_MS)
        ) {
            return cachedGamePackageResult
        }

        val result = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            // Android's ApplicationInfo.CATEGORY_GAME value.
            info.category == APPLICATION_CATEGORY_GAME ||
                context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(INTENT_CATEGORY_GAME)
                        .setPackage(packageName),
                    0
                ).isNotEmpty()
        }.getOrDefault(false)
        cachedGamePackageName = packageName
        lastGamePackageQueryAt = now
        cachedGamePackageResult = result
        return result
    }

    private fun isCacheFresh(lastReadAt: Long, nowElapsed: Long, maxAge: Long): Boolean =
        lastReadAt >= 0L && nowElapsed >= lastReadAt && nowElapsed - lastReadAt < maxAge

    private fun resolveDefaultPresetName(): String? =
        presetRepository.list().firstOrNull { it.isAppProfileDefault && it.name.isNotBlank() }?.name

    private fun loadPresetByName(name: String): LedPreset? =
        presetRepository.list().firstOrNull { it.name == name }

    // ── Pending projection token ──────────────────────────────────────────

    fun setPendingProjectionToken(packageName: String, presetName: String) {
        Log.d(TAG, "setPendingProjectionToken: packageName=$packageName, presetName=$presetName")
        prefs.edit()
            .putString(PREF_KEY_PENDING_PROJECTION_PACKAGE, packageName)
            .putString(PREF_KEY_PENDING_PROJECTION_PRESET, presetName)
            .putBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, false)
            .apply()
    }

    fun getPendingProjectionPackage(): String? =
        prefs.getString(PREF_KEY_PENDING_PROJECTION_PACKAGE, null)?.takeIf { it.isNotBlank() }

    fun getPendingProjectionPresetName(): String? =
        prefs.getString(PREF_KEY_PENDING_PROJECTION_PRESET, null)?.takeIf { it.isNotBlank() }

    fun hasPendingProjectionToken(): Boolean =
        getPendingProjectionPackage() != null

    fun isPendingProjectionNotified(): Boolean =
        prefs.getBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, false)

    fun markPendingProjectionNotified() {
        prefs.edit().putBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, true).apply()
    }

    fun clearPendingProjectionToken() {
        Log.d(TAG, "clearPendingProjectionToken: clearing pending projection state")
        prefs.edit()
            .remove(PREF_KEY_PENDING_PROJECTION_PACKAGE)
            .remove(PREF_KEY_PENDING_PROJECTION_PRESET)
            .remove(PREF_KEY_PENDING_PROJECTION_NOTIFIED)
            .apply()
    }

    /**
     * Force the next [checkForSwitch] to re-evaluate even if the preset name
     * would normally match the dedup cache.  This is used after projection
     * data is supplied so the MP-requiring preset is actually applied.
     */
    fun forceNextResolution() {
        Log.d(TAG, "forceNextResolution: clearing lastResolvedPresetName so next check forces a result")
        lastResolvedPresetName = null
        hasResolvedPresetOnce = false
    }
}

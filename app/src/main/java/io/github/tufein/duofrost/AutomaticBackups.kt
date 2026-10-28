package io.github.tufein.duofrost

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Event-driven backups. Destination, grants and operational state never enter an archive. */
object AutomaticBackups {
    private var installedContext: Context? = null
    private var pending: ScheduledFuture<*>? = null
    private val worker by lazy { Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "DuoFrost-backup").apply { isDaemon = true }
    } }
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    fun state(context: Context): SharedPreferences =
        context.getSharedPreferences("duofrost_auto_backups", Context.MODE_PRIVATE)

    fun important(key: String?): Boolean = key == null || key in BehaviorSettingsBackup.keys ||
        key in setOf("presets_json", "selected_ui_theme", "custom_ui_themes_json", "colored_logo_enabled",
            "app_profile_mappings", "auto_switch_enabled", "game_scene_enabled", "game_scene_preset",
            "smart_scenes_enabled", "scenes_rules_json", "scenes_groups_json", PresetLibraryStore.PREF_KEY_LIBRARY)

    @Synchronized fun install(context: Context) {
        val app = context.applicationContext
        if (installedContext === app) return
        listener?.let { old -> installedContext?.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
            ?.unregisterOnSharedPreferenceChangeListener(old) }
        pending?.cancel(false)
        installedContext = app
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (important(key) && state(app).getBoolean("enabled", false)) request(app)
        }
        app.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized fun request(context: Context, now: Boolean = false) {
        val app = context.applicationContext
        pending?.cancel(false)
        pending = worker.schedule({ if (now || state(app).getBoolean("enabled", false)) export(app) }, if (now) 0 else 3, TimeUnit.SECONDS)
    }

    internal fun export(context: Context) {
        val prefs = state(context)
        val folder = prefs.getString("folder", null) ?: return
        var created: Uri? = null
        try {
            prefs.edit().putString("status", "Saving backup…").apply()
            val tree = Uri.parse(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
            created = DocumentsContract.createDocument(context.contentResolver, parent, "application/zip",
                "DuoFrost-auto-$timestamp-${UUID.randomUUID()}.zip")
                ?: error("This folder cannot create backup files")
            val result = BackupArchiveTransfer.exportToUri(context, created)
            prefs.edit().putLong("last_success", System.currentTimeMillis())
                .putString("status", if (result.warnings.isEmpty()) "Backup saved"
                    else "Backup saved with ${result.warnings.size} warnings; keep your earlier backups").apply()
        } catch (error: Exception) {
            created?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            prefs.edit().putString("status", "Backup failed. Check folder access and free space; earlier backups were kept.").apply()
        }
    }
}

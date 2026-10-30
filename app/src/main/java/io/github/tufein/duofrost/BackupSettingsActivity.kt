package io.github.tufein.duofrost

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import io.github.tufein.duofrost.ui.ThemedActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupSettingsActivity : ThemedActivity() {
    private val state by lazy { AutomaticBackups.state(this) }
    private lateinit var summary: TextView
    private lateinit var enabled: SwitchMaterial
    private var syncing = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { render() } }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            state.edit().putString("folder", uri.toString()).putBoolean("enabled", true).apply()
            AutomaticBackups.request(this, true)
        }.onFailure { Toast.makeText(this, "Choose a writable folder that supports persistent access", Toast.LENGTH_LONG).show() }
        render()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomaticBackups.install(applicationContext)
        val (root, content) = SecondaryScreenUi.screen(this, "Automatic backups",
            "Save a full backup after important changes to presets, scenes, themes or settings. Changes within three seconds are combined. No periodic polling or extra lighting service is used. Earlier backups are never overwritten or removed.")
        summary = SecondaryScreenUi.body(this, "")
        content.addView(summary)
        content.addView(SecondaryScreenUi.action(this, "Choose backup folder") { folderPicker.launch(null) })
        enabled = SecondaryScreenUi.toggle(this, "Back up after changes", false) { value ->
            if (!syncing) {
                if (value && state.getString("folder", null) == null) folderPicker.launch(null)
                else state.edit().putBoolean("enabled", value).apply()
                render()
            }
        }
        content.addView(enabled)
        content.addView(SecondaryScreenUi.action(this, "Back up now") {
            if (state.getString("folder", null) == null) folderPicker.launch(null)
            else AutomaticBackups.request(this, true)
        })
        content.addView(SecondaryScreenUi.body(this,
            "Backups contain your selected categories, including preset images. Keep them in a private folder. Folder permissions and automatic-backup activation are local to this installation and are never exported. After reinstalling, choose the folder again. A system kill before a pending save can interrupt it."))
        setContentView(root)
        render()
    }
    override fun onStart() {
        super.onStart()
        state.registerOnSharedPreferenceChangeListener(listener)
        render()
    }
    override fun onStop() { state.unregisterOnSharedPreferenceChangeListener(listener); super.onStop() }
    private fun render() {
        if (!::summary.isInitialized) return
        syncing = true
        enabled.isChecked = state.getBoolean("enabled", false) && state.getString("folder", null) != null
        syncing = false
        val last = state.getLong("last_success", 0)
        summary.text = (if (state.getString("folder", null) == null) "No folder selected" else "Backup folder selected") +
            "\n" + state.getString("status", "No backup saved yet") +
            if (last > 0) "\nLast successful backup: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(last)) else ""
    }
}

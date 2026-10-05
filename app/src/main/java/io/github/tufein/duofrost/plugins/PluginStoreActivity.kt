package io.github.tufein.duofrost.plugins

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import io.github.tufein.duofrost.services.DuoFrostAccessibilityService
import io.github.tufein.duofrost.ui.SecondaryScreenUi

/**
 * The plugin store screen. Lists the catalogue, installs/updates/removes
 * plugins, and exposes the "check for updates at launch" setting. Built
 * programmatically (no XML) to stay self-contained and not touch the main UI.
 *
 * Network + install run on background threads; the UI is rebuilt on the main
 * thread after each action.
 */
class PluginStoreActivity : AppCompatActivity() {

    private val prefs by lazy { PluginPrefs.prefs(this) }
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView

    private var catalog: PluginCatalog? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Plugins"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(buildRoot())
        refreshCatalog()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ---- UI scaffold ----------------------------------------------------

    private fun buildRoot(): View {
        val (scroll, root) = SecondaryScreenUi.screen(this, "Plugins",
            "Add lighting packs for your games and apps. You choose when to install or update them.")

        // "Check for updates at launch" toggle (mirrors the persisted setting).
        root.addView(SecondaryScreenUi.toggle(this, "Check for updates at launch",
            PluginPrefs.checkUpdatesAtLaunch(prefs)) { checked ->
            PluginPrefs.setCheckUpdatesAtLaunch(prefs, checked)
        })

        root.addView(SecondaryScreenUi.action(this, "Refresh catalogue", primary = true) { refreshCatalog() })

        statusText = SecondaryScreenUi.body(this, "").apply {
            setPadding(0, dp(16), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(statusText)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        return scroll
    }

    // ---- catalogue load + render ----------------------------------------

    private fun refreshCatalog() {
        if (loading) return
        loading = true
        statusText.text = "Loading catalogue…"
        listContainer.removeAllViews()
        val url = PluginPrefs.catalogUrl(prefs)
        Thread {
            val result = PluginRepository.fetchCatalog(url)
            runOnUiThread {
                loading = false
                when (result) {
                    is PluginRepository.CatalogResult.Success -> {
                        catalog = result.catalog
                        renderCatalog(result.catalog)
                    }
                    is PluginRepository.CatalogResult.Failure -> {
                        statusText.text = "Couldn't load plugins:\n${result.message}\n\nTry refreshing the catalogue again."
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderCatalog(catalog: PluginCatalog) {
        listContainer.removeAllViews()
        if (catalog.plugins.isEmpty()) {
            statusText.text = "No plugins in the catalogue yet."
            return
        }
        statusText.text = if (catalog.plugins.size == 1) "1 plugin available." else "${catalog.plugins.size} plugins available."
        catalog.plugins.forEach { listContainer.addView(pluginRow(it)) }
    }

    private fun pluginRow(entry: CatalogEntry): View {
        val installed = PluginPrefs.installedVersion(prefs, entry.id)
        val hasUpdate = installed != null && entry.version > installed

        val (card, content) = SecondaryScreenUi.card(this)

        content.addView(SecondaryScreenUi.body(this, entry.name, secondary = false).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(SecondaryScreenUi.body(this, "Version ${entry.versionName}")
            .apply { setPadding(0, dp(4), 0, 0) })
        if (entry.author.isNotBlank()) {
            content.addView(SecondaryScreenUi.body(this, "By ${entry.author}"))
        }
        if (entry.description.isNotBlank()) {
            content.addView(SecondaryScreenUi.body(this, entry.description, secondary = false)
                .apply { setPadding(0, dp(12), 0, dp(12)) })
        }
        content.addView(SecondaryScreenUi.body(this,
            when {
                hasUpdate -> "Installed v$installed — update available"
                installed != null -> "Installed"
                else -> "Not installed"
            }))

        // Per-plugin "mirror screen" toggle — only the Fallout plugin honours it,
        // and only once installed. ON = AMBIENT-mirror whichever display shows the
        // Pip-Boy instead of the event-driven feed (heavier; needs the a11y service).
        if (entry.id == PluginPrefs.FALLOUT_PLUGIN_ID && installed != null) {
            content.addView(SecondaryScreenUi.body(this,
                "Screen mirroring uses more resources and needs Accessibility access.")
                .apply { setPadding(0, dp(16), 0, 0) })
            content.addView(SecondaryScreenUi.toggle(this, "Mirror bottom screen",
                PluginPrefs.isMirrorScreen(prefs, entry.id)) { checked ->
                    PluginPrefs.setMirrorScreen(prefs, entry.id, checked)
                    toast(
                        when {
                            checked && !DuoFrostAccessibilityService.isEnabled(this@PluginStoreActivity) ->
                                "Mirror on — enable DuoFrost's accessibility service in Settings to capture the screen"
                            checked -> "Mirror mode on"
                            else -> "Mirror mode off"
                        }
                    )
            })
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        if (installed == null) {
            buttons.addView(actionButton("Install") { doInstall(entry) })
        } else {
            if (hasUpdate) buttons.addView(actionButton("Update to v${entry.versionName}") { doInstall(entry) })
            buttons.addView(actionButton("Uninstall") { doUninstall(entry) })
        }
        content.addView(buttons)
        return card
    }

    private fun actionButton(label: String, onClick: () -> Unit): MaterialButton =
        SecondaryScreenUi.action(this, label, onClick = onClick)

    // ---- actions --------------------------------------------------------

    private fun doInstall(entry: CatalogEntry) {
        statusText.text = "Installing ${entry.name}…"
        Thread {
            val result = PluginInstaller.install(this, prefs, entry)
            runOnUiThread {
                when (result) {
                    is PluginInstaller.Result.Success ->
                        toast("Installed ${entry.name}")
                    is PluginInstaller.Result.Failure ->
                        toast("Install failed: ${result.message}")
                }
                catalog?.let(::renderCatalog)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun doUninstall(entry: CatalogEntry) {
        val result = PluginInstaller.uninstall(prefs, entry)
        when (result) {
            is PluginInstaller.Result.Success -> toast("Removed ${entry.name}")
            is PluginInstaller.Result.Failure -> toast("Uninstall failed: ${result.message}")
        }
        catalog?.let(::renderCatalog)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

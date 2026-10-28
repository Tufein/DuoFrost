package io.github.tufein.duofrost

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import io.github.tufein.duofrost.tools.LedTransition
import io.github.tufein.duofrost.ui.*
import io.github.tufein.duofrost.scenes.*
import io.github.tufein.duofrost.services.LEDService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoadmapFeaturesTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
    private fun preset(id: String, color: Int = 0xff0000) = LedPreset(id, LedAnimationType.STATIC,
        PerformanceProfile.HIGH, color, brightness = 255, speed = 0.5f, smoothness = 0.5f, id = id)
    private fun theme() = ThemeCatalog.decode(JSONObject().apply {
        put("id", "custom_test"); put("name", "My teal")
        put("background", "#081A1F"); put("card", "#102A30"); put("surface", "#193B42")
        put("text", "#E6F6F5"); put("accent", "#008577"); put("accentLight", "#5CE0C4")
    })
    @Before fun setup() {
        prefs.edit().clear().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true).putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("colored_logo_enabled", false).commit()
        AutomaticBackups.state(app).edit().clear().commit()
        app.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
        LEDService.isRunning = false
    }

    @Test fun customThemeRoundTripsIndependentlyAndRejectsUnusableColors() {
        val original = theme()
        ThemeCatalog.save(prefs, original)
        prefs.edit().putString("selected_ui_theme", original.id).commit()
        val uri = Uri.fromFile(File(app.cacheDir, "theme.json"))
        ThemeArchiveTransfer.exportToUri(app, uri, original.id, false)
        val imported = ThemeArchiveTransfer.importFromUri(app, uri)
        assertTrue(imported.errors.isEmpty())
        assertEquals(original, imported.customTheme)
        assertFalse(imported.coloredLogoEnabled)
        val bad = ThemeCatalog.encode(original).put("text", "#102A30")
        assertTrue(runCatching { ThemeCatalog.decode(bad) }.isFailure)
        assertEquals(original, ThemeCatalog.selected(app))
        prefs.edit().putInt(ThemeCatalog.CUSTOM_KEY, 123).commit()
        assertEquals(ThemeCatalog.builtIns.first(), ThemeCatalog.selected(app))
        assertTrue(runCatching { ThemeCatalog.save(prefs, original) }.isFailure)
        assertEquals(123, prefs.getInt(ThemeCatalog.CUSTOM_KEY, 0))
    }

    @Test fun secondaryScreensAndDialogsUseTheCustomPaletteWithoutRecoloringLedSwatches() {
        val t = theme()
        ThemeCatalog.save(prefs, t)
        prefs.edit().putString("selected_ui_theme", t.id).commit()
        val activity = Robolectric.buildActivity(StartupGuideActivity::class.java).setup().get()
        assertEquals(t.backgroundColor, (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).background as ColorDrawable).color)
        val content = android.widget.LinearLayout(activity)
        val text = TextView(activity).apply { setTextColor(activity.getColor(R.color.bifrost_text)) }
        val swatch = View(activity).apply { setBackgroundColor(activity.getColor(R.color.bifrost_bg)) }
        content.addView(text); content.addView(swatch)
        var userCallback = false
        val dialog = ThemedDialogBuilder(activity).setView(content).setPositiveButton("OK", null).create()
        dialog.setOnShowListener { userCallback = true }
        dialog.show()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(userCallback)
        assertEquals(t.textColor, text.currentTextColor)
        assertEquals(activity.getColor(R.color.bifrost_bg), (swatch.background as ColorDrawable).color)
        dialog.dismiss()
        activity.finish()
    }

    @Test fun themeEditorPreservesUncommittedInputsAcrossRotationWithoutChangingPreferences() {
        var controller = Robolectric.buildActivity(ThemeEditorActivity::class.java).setup()
        fun edit(view: View, label: String): EditText? {
            if (view is EditText && view.contentDescription == label) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) edit(view.getChildAt(i), label)?.let { return it }
            return null
        }
        edit(controller.get().window.decorView, "Theme name")!!.setText("Draft color")
        val before = prefs.all
        val state = Bundle()
        controller.saveInstanceState(state).pause().stop().destroy()
        controller = Robolectric.buildActivity(ThemeEditorActivity::class.java).create(state).start().resume().visible()
        assertEquals("Draft color", edit(controller.get().window.decorView, "Theme name")!!.text.toString())
        assertEquals(before, prefs.all)
        controller.pause().stop().destroy()
    }

    @Test fun dashboardCanHideOptionalSectionsAndReorderCardsWhileKeepingStartStop() {
        prefs.edit().putBoolean("dashboard_library_first", true).putBoolean("dashboard_show_library", false)
            .putBoolean("dashboard_show_output", false).putBoolean("dashboard_show_quick", false).commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(R.id.gui_libraryCard, activity.findViewById<ViewGroup>(R.id.gui_homeContent).getChildAt(0).id)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.gui_libraryCard).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.gui_outputSeekBar).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.serviceToggle).visibility)
        assertEquals(true, BehaviorSettingsBackup.decode("dashboard_library_first", "boolean", true))
        assertNull(shadowOf(app).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun simulationUsesAppPriorityAndModifiersWithoutTouchingPrefsOrService() {
        val presets = listOf(preset("manual"), preset("game"), preset("mapped"), preset("night"))
        val rules = listOf(SceneRule("night", "Night", presetId = "night", maxBrightnessPercent = 20),
            SceneRule("dim", "Dim", maxBrightnessPercent = 40))
        val before = prefs.all
        val result = SceneSimulator.evaluate(presets, rules, emptyList(),
            SceneContext(LocalDateTime.of(2026, 10, 28, 23, 0), "org.game", true, false, 50, false),
            mapOf("org.game" to "mapped"), true, true, "game", "manual", null, app.packageName)
        assertEquals("mapped", result.preset?.id)
        assertEquals(SceneSelectionPolicy.Source.APP_PROFILE, result.source)
        assertEquals(40, result.limit)
        val hold = SceneSimulator.evaluate(presets, rules, emptyList(),
            SceneContext(LocalDateTime.of(2026, 10, 28, 23, 0), "org.game", true, false, 50, false),
            emptyMap(), false, false, null, "manual", "game", app.packageName)
        assertEquals(SceneSelectionPolicy.Source.TEMPORARY, hold.source)
        assertEquals("game", hold.preset?.id)
        assertEquals(before, prefs.all)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun transitionMetadataSurvivesStorageDraftAndServiceBaselineWithoutBecomingLightingChange() {
        val p = preset("one").copy(transitionDurationMs = 500)
        assertEquals(p, PresetCodec.decode(PresetCodec.encode(p)))
        assertEquals(500, EditorDraftCodec.decode(EditorDraftCodec.encode(p))!!.transitionDurationMs)
        val intent = android.content.Intent().putExtra("animationType", "STATIC").putExtra("presetTransitionDurationMs", 500)
        assertEquals(500, SceneBaseline.fromIntent(intent, p.id, p.name).transitionDurationMs)
        assertFalse(PresetController.hasLightingChanges(p, p.copy(transitionDurationMs = 0)))
        assertEquals(0, PresetCodec.decode(PresetCodec.encode(p).put("transitionDurationMs", 999999)).transitionDurationMs)
    }

    @Test fun simulatorScreenDoesNotMigrateOrRewriteLegacyPresetData() {
        prefs.edit().putString("presets_json", """[{"name":"Legacy","animationType":"STATIC"}]""")
            .putString("last_preset_name", "Legacy").commit()
        val before = prefs.all
        val controller = Robolectric.buildActivity(SceneSimulatorActivity::class.java).setup()
        fun simulate(view: View): View? {
            if (view is TextView && view.text.toString() == "Simulate") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) simulate(view.getChildAt(i))?.let { return it }
            return null
        }
        simulate(controller.get().window.decorView)!!.performClick()
        assertEquals(before, prefs.all)
        assertNull(shadowOf(app).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun fadesWaitForTheIncomingFrameAndRemainBoundedDuringRapidChangesAndCancellation() {
        val fade = LedTransition()
        fade.begin(intArrayOf(0xff0000, 0, 0x0000ff, 0), 500)
        assertEquals(0xff0000, fade.color(0, 0x0000ff, 999999))
        fade.frameArrived(1000)
        assertEquals(0x7f007f, fade.color(0, 0x0000ff, 1250))
        assertEquals(0x007f7f, fade.color(2, 0x00ff00, 1250))
        val current = fade.color(0, 0x0000ff, 1250)
        fade.begin(IntArray(4) { current }, 250)
        fade.frameArrived(1250)
        assertEquals(current, fade.color(0, 0x00ff00, 1250))
        assertEquals(0x00ff00, fade.color(0, 0x00ff00, 1500))
        assertFalse(fade.active(1500))
        fade.cancel()
        assertEquals(0, fade.color(0, 0, 1501))
        assertFalse(fade.active(1501))
    }

    @Test fun realOutputBlendsBothSticksUnderTheCeilingAndNeverRepaintsAfterStop() {
        val writes = mutableListOf<String>()
        val binder = object : android.os.Binder() {
            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                writes += data.createStringArray()!![0]
                return true
            }
        }
        val controller = io.github.tufein.duofrost.tools.LedController(binder)
        controller.setOutputBrightnessLimit(64)
        controller.setLedColor(255, 0, 0)
        controller.beginTransition(500)
        controller.setLedColorDual(0, 0, 255, 0, 255, 0)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
        assertTrue(writes.last().contains("64:0:64"))
        assertTrue(writes.last().contains("64:64:0"))
        for (write in writes) for (frame in Regex("echo [12]-(\\d+):(\\d+):(\\d+):").findAll(write)) {
            for (part in frame.groupValues.drop(1)) assertTrue(part.toInt() in 0..64)
        }
        controller.setOutputBrightnessLimit(0)
        assertEquals(4, Regex("0:0:0:255").findAll(writes.last()).count())
        controller.cancelTransition()
        controller.setLedColor(0, 0, 0, 0)
        controller.shutdown()
        val afterStop = writes.toList()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertEquals(afterStop, writes)
    }

    @Test fun automaticBackupsIgnoreRuntimeChangesAndSaveAnImportableArchiveThroughTheFolderProvider() {
        assertFalse(AutomaticBackups.important("last_preset_name"))
        assertFalse(AutomaticBackups.important("pending_projection_package"))
        assertTrue(AutomaticBackups.important("presets_json"))
        assertTrue(AutomaticBackups.important("dashboard_show_library"))
        val provider = BackupProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = "duofrost.backups" })
        ShadowContentResolver.registerProviderInternal("duofrost.backups", provider)
        val state = AutomaticBackups.state(app)
        state.edit().putString("folder", "content://duofrost.backups/tree/root").commit()
        ThemeCatalog.save(prefs, theme())
        prefs.edit().putString("selected_ui_theme", "custom_test").putBoolean("dashboard_show_library", false)
            .putString("mediaProjectionData", "never export this").commit()
        AutomaticBackups.export(app)
        assertEquals("Backup saved", state.getString("status", null))
        assertTrue(state.getLong("last_success", 0) > 0)
        val exported = JSONObject(ZipInputStream(provider.file.inputStream()).use { zip ->
            var text = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "prefs.json") text = zip.readBytes().toString(Charsets.UTF_8)
            }
            text
        })
        assertTrue(exported.toString().contains("custom_ui_themes_json"))
        assertTrue(exported.toString().contains("dashboard_show_library"))
        assertFalse(exported.toString().contains("mediaProjectionData"))
        assertFalse(exported.toString().contains("duofrost.backups"))
        val last = state.getLong("last_success", 0)
        provider.fail = true
        AutomaticBackups.export(app)
        assertTrue(state.getString("status", "")!!.startsWith("Backup failed"))
        assertEquals(last, state.getLong("last_success", 0))
        assertEquals(1, provider.deletions)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun importantChangesAutomaticallyProduceOneCoalescedBackup() {
        val provider = BackupProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = "duofrost.backups" })
        ShadowContentResolver.registerProviderInternal("duofrost.backups", provider)
        val state = AutomaticBackups.state(app)
        state.edit().putString("folder", "content://duofrost.backups/tree/root").putBoolean("enabled", true).commit()
        AutomaticBackups.install(app)
        prefs.edit().putBoolean("dashboard_show_library", false).putBoolean("dashboard_show_output", false)
            .putString("selected_ui_theme", "oled_blue").commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
        while (state.getString("status", "") != "Backup saved" && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals("Backup saved", state.getString("status", ""))
        assertEquals(1, provider.creations)
        state.edit().putBoolean("enabled", false).commit()
        assertNull(shadowOf(app).nextStartedService)
    }

    class BackupProvider : ContentProvider() {
        lateinit var file: File
        var fail = false
        var deletions = 0
        @Volatile var creations = 0
        override fun onCreate(): Boolean { file = File(context!!.cacheDir, "automatic-backup.zip"); return true }
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            if (method.contains("deleteDocument")) { deletions++; return Bundle() }
            creations++
            return Bundle().apply { putParcelable("uri", Uri.parse("content://duofrost.backups/document/backup")) }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (fail) throw IOException("Test folder unavailable")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, sort: String?): Cursor? = null
        override fun getType(uri: Uri) = "application/zip"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
}

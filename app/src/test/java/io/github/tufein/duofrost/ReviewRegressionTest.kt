package io.github.tufein.duofrost

import android.app.Application
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import io.github.tufein.duofrost.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReviewRegressionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
    @Before fun setup() {
        prefs.edit().clear().putBoolean(StartupGuideActivity.PREF_DONE, true)
            .putBoolean("first_launch_alert_shown", true).putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("colored_logo_enabled", false).commit()
        AutomaticBackups.state(app).edit().clear().commit()
        io.github.tufein.duofrost.services.LEDService.isRunning = false
        MainActivity.mediaProjectionResultCode = null
        MainActivity.mediaProjectionData = null
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun returningToAnExistingScreenRecolorsTextButtonsAndNestedBackgrounds() {
        prefs.edit().putString("selected_ui_theme", "oled_blue").commit()
        val controller = Robolectric.buildActivity(DashboardSettingsActivity::class.java).setup()
        val activity = controller.get()
        val views = descendants(activity.window.decorView)
        val heading = views.filterIsInstance<TextView>().first { it.text.toString() == "Dashboard layout" }
        val button = views.filterIsInstance<MaterialButton>().first { it.text.toString() == "Restore default layout" }
        val scroll = views.filterIsInstance<android.widget.ScrollView>().first()
        controller.pause().stop()
        prefs.edit().putString("selected_ui_theme", "midnight_teal").commit()
        controller.start().resume()
        val theme = ThemeCatalog.selected(activity)
        assertEquals(theme.textColor, heading.currentTextColor)
        assertEquals(theme.surfaceColor, button.backgroundTintList!!.defaultColor)
        assertEquals(theme.backgroundColor, (scroll.background as ColorDrawable).color)
        controller.pause().stop().destroy()
    }

    @Test fun importingANewCustomThemeRebuildsTheSpinnerBeforeSelectingIt() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val theme = ThemeCatalog.builtIns.first().copy(id = "custom_import", label = "Imported theme")
        ThemeCatalog.save(prefs, theme)
        MainActivity::class.java.getDeclaredMethod("applyImportedTheme", UiTheme::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, theme, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val spinner = activity.findViewById<Spinner>(R.id.themeSpinner)
        assertEquals(ThemeCatalog.all(prefs).size, spinner.adapter.count)
        assertEquals("Imported theme", spinner.selectedItem)
        assertEquals(theme.id, prefs.getString("selected_ui_theme", null))
        controller.pause().stop().destroy()
    }

    @Test fun backupStatusRefreshesWhenReturningAfterAWorkerFinishes() {
        val state = AutomaticBackups.state(app)
        val controller = Robolectric.buildActivity(BackupSettingsActivity::class.java).setup()
        controller.pause().stop()
        state.edit().putString("status", "Backup failed. Test failure while screen was hidden.").commit()
        controller.start().resume()
        assertTrue(descendants(controller.get().window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("Test failure while screen was hidden") })
        controller.pause().stop().destroy()
    }

    @Test fun themedDialogsCloseWithTheirHostWithoutCallingCancelOrConfirm() {
        val controller = Robolectric.buildActivity(DashboardSettingsActivity::class.java).setup()
        var actions = 0
        var dismissed = 0
        val dialog = ThemedDialogBuilder(controller.get()).setMessage("Lifecycle test")
            .setPositiveButton("OK") { _, _ -> actions++ }.setNegativeButton("Cancel") { _, _ -> actions++ }
            .create()
        dialog.setOnDismissListener { dismissed++ }
        dialog.show()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertEquals(0, actions)
        assertEquals(1, dismissed)
    }

    @Test fun themeImportsRejectOversizedFilesWithoutAcceptingThePalette() {
        val file = File(app.cacheDir, "oversized-theme.json")
        file.writeText("""{"schema":"bifrost_theme_bundle","version":1,"themeId":"classic","padding":"""" +
            "x".repeat(150_000) + "\"}")
        val result = ThemeArchiveTransfer.importFromUri(app, Uri.fromFile(file))
        assertTrue(result.errors.isNotEmpty())
        assertNull(result.themeId)
    }

    @Test fun exportingAnUnavailableThemeDoesNotWriteAnUnusableBundle() {
        val file = File(app.cacheDir, "missing-theme.json")
        file.writeText("keep existing export")
        assertTrue(runCatching {
            ThemeArchiveTransfer.exportToUri(app, Uri.fromFile(file), "custom_missing", true)
        }.isFailure)
        assertEquals("keep existing export", file.readText())
    }

    @Test fun dynamicallyAddedSwitchesUseTheSelectedThemeImmediately() {
        prefs.edit().putString("selected_ui_theme", "oled_green").commit()
        val controller = Robolectric.buildActivity(DashboardSettingsActivity::class.java).setup()
        val toggle = SecondaryScreenUi.toggle(controller.get(), "Dynamic rule", true) {}
        val theme = ThemeCatalog.selected(app)
        assertEquals(theme.accentLightColor, toggle.thumbTintList!!.getColorForState(
            intArrayOf(android.R.attr.state_checked), 0))
        controller.pause().stop().destroy()
    }

    @Test fun dialogActionsRemainReadableWhenACustomHighlightMatchesTheCard() {
        val theme = ThemeCatalog.builtIns.first().copy(id = "custom_low-accent", label = "Low accent",
            accentLightColor = ThemeCatalog.builtIns.first().cardColor)
        ThemeCatalog.save(prefs, theme)
        prefs.edit().putString("selected_ui_theme", theme.id).commit()
        val controller = Robolectric.buildActivity(DashboardSettingsActivity::class.java).setup()
        val dialog = ThemedDialogBuilder(controller.get()).setPositiveButton("OK", null).create()
        dialog.show()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val color = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).currentTextColor
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(color, theme.cardColor) >= 4.5)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    private fun backup(prefsJson: String, duplicate: Boolean = false): Uri {
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry("manifest.json", """{"schema":"bifrost_full_backup","version":2}""")
            entry("prefs.json", prefsJson)
            if (duplicate) entry("prefx.json", prefsJson)
        }
        val file = File(app.cacheDir, "review-backup.zip")
        file.writeBytes(if (duplicate) bytes.toByteArray().toString(Charsets.ISO_8859_1)
            .replace("prefx.json", "prefs.json").toByteArray(Charsets.ISO_8859_1) else bytes.toByteArray())
        return Uri.fromFile(file)
    }

    @Test fun fullBackupRejectsWrongPreferenceTypesBeforeChangingExistingSettings() {
        prefs.edit().putString("selected_ui_theme", "oled_green").commit()
        val before = prefs.all
        val uri = backup("""{"items":[{"key":"selected_ui_theme","type":"int","value":123}]}""")
        val result = BackupArchiveTransfer.importFromUri(app, uri)
        assertTrue(result.errors.any { it.contains("invalid type") })
        assertEquals(before, prefs.all)
    }

    @Test fun fullBackupRejectsDuplicateFilesBeforeChangingExistingSettings() {
        val before = prefs.all
        val result = BackupArchiveTransfer.importFromUri(app, backup("""{"items":[]}""", true))
        assertTrue(result.errors.any { it.contains("duplicate") })
        assertEquals(before, prefs.all)
    }

    @Test fun fullBackupBoundsActualExpandedDataBeforeParsingPreferences() {
        val before = prefs.all
        val result = BackupArchiveTransfer.importFromUri(app, backup("x".repeat(8 * 1024 * 1024 + 1)))
        assertTrue(result.errors.any { it.contains("byte limit") })
        assertEquals(before, prefs.all)
    }

    @Test fun audioCaptureRequestsAudioAccessBeforeScreenConsentAndDenialStartsNothing() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        MainActivity::class.java.getDeclaredField("selectedAnimationType").apply { isAccessible = true }
            .set(activity, io.github.tufein.duofrost.animations.LedAnimationType.AUDIO_REACTIVE)
        MainActivity::class.java.getDeclaredMethod("requestScreenCapturePermission").apply { isAccessible = true }.invoke(activity)
        val request = shadowOf(activity).lastRequestedPermission!!
        assertArrayEquals(arrayOf(android.Manifest.permission.RECORD_AUDIO), request.requestedPermissions)
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", shadowOf(activity).nextStartedActivityForResult!!.intent.action)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
            intArrayOf(android.content.pm.PackageManager.PERMISSION_DENIED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(shadowOf(app).nextStartedService)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        controller.pause().stop().destroy()
    }

    @Test fun grantingAudioAccessContinuesToScreenConsentWithoutStartingLighting() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        MainActivity::class.java.getDeclaredField("selectedAnimationType").apply { isAccessible = true }
            .set(activity, io.github.tufein.duofrost.animations.LedAnimationType.AUDIO_REACTIVE)
        MainActivity::class.java.getDeclaredMethod("requestScreenCapturePermission").apply { isAccessible = true }.invoke(activity)
        val request = shadowOf(activity).lastRequestedPermission!!
        shadowOf(activity).nextStartedActivityForResult
        shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
            intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val capture = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(capture)
        assertNotEquals("android.content.pm.action.REQUEST_PERMISSIONS", capture!!.intent.action)
        assertNull(shadowOf(app).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun audioAccessRemainsOptionalForOnboardingAndStaticLighting() {
        val controller = Robolectric.buildActivity(StartupGuideActivity::class.java).setup()
        assertNotNull(controller.get().findViewById<View>(R.id.buttonGrantAudio))
        assertNull(shadowOf(controller.get()).lastRequestedPermission)
        controller.pause().stop().destroy()
        val main = Robolectric.buildActivity(MainActivity::class.java).setup()
        val requested = MainActivity::class.java.getDeclaredMethod("requestMissingAudioPermission",
            io.github.tufein.duofrost.animations.LedAnimationType::class.java).apply { isAccessible = true }
            .invoke(main.get(), io.github.tufein.duofrost.animations.LedAnimationType.STATIC)
        assertEquals(false, requested)
        assertNull(shadowOf(main.get()).lastRequestedPermission)
        main.pause().stop().destroy()
    }

    @Test fun serviceWaitsForAudioAccessEvenWhenItAlreadyHasProjectionData() {
        val serviceController = Robolectric.buildService(io.github.tufein.duofrost.services.LEDService::class.java).create()
        val service = serviceController.get()
        fun set(name: String, value: Any) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        io.github.tufein.duofrost.services.LEDService.isRunning = true
        set("currentAnimationType", io.github.tufein.duofrost.animations.LedAnimationType.AUDIO_REACTIVE)
        set("lastProjectionData", android.content.Intent("test-unused-consent"))
        set("lastProjectionResultCode", android.app.Activity.RESULT_OK)
        service.javaClass.getDeclaredMethod("restartAnimationForCurrentState", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(service, true)
        assertTrue(io.github.tufein.duofrost.services.LEDService.isWaitingForCapturePermission)
        assertNull(service.javaClass.getDeclaredField("pendingTransitionRunnable").apply { isAccessible = true }.get(service))
        serviceController.destroy()
        assertFalse(io.github.tufein.duofrost.services.LEDService.isRunning)
    }
}

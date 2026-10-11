package io.github.tufein.duofrost

import android.content.Context
import android.content.SharedPreferences
import android.content.DialogInterface
import android.os.Looper
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresetControllerPersistenceTest {
    private lateinit var activity: AppCompatActivity
    private lateinit var prefs: SharedPreferences
    private lateinit var controller: PresetController
    private lateinit var modifyButton: MaterialButton
    private lateinit var profileManager: AppProfileManager
    private val callbacks = mutableListOf<String>()
    private var updating = false
    private var appliedCount = 0
    private var uiAppliedCount = 0
    private var renamedCount = 0
    private val saved = LedPreset("Ocean", LedAnimationType.STATIC, PerformanceProfile.HIGH,
        0x123456, brightness = 180, speed = .5f, smoothness = .5f, id = "ocean")

    @Before fun setup() {
        val activityBuilder = Robolectric.buildActivity(AppCompatActivity::class.java)
        activityBuilder.get().setTheme(R.style.Theme_DuoFrost)
        activity = activityBuilder.setup().get()
        prefs = activity.getSharedPreferences("controller-persistence", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        PresetRepository(prefs).save(listOf(saved))
        profileManager = AppProfileManager(prefs)
        modifyButton = MaterialButton(activity)
        controller = PresetController(
            activity, prefs, Spinner(activity), MaterialButton(activity), modifyButton, MaterialButton(activity),
            getCurrentConfig = { saved.copy(brightness = 99) },
            applyPresetToUi = {
                uiAppliedCount++
                callbacks += "ui"
                profileManager.clearGameSceneIfMissing(PresetRepository(prefs).list().map { it.name })
            }, markIsUpdatingFromPreset = { updating = it },
            isUpdatingFromPreset = { updating }, onPresetApplied = {
                appliedCount++
                callbacks += "applied"
            },
            onRequestCustomPresetImage = {}, onPresetRenamed = { oldName, newName ->
                renamedCount++
                callbacks += "renamed"
                profileManager.renamePresetInMappings(oldName, newName)
                profileManager.renameGameScenePreset(oldName, newName)
            }
        )
        controller.init(saved)
    }

    @Test fun rejectedEditorSaveDoesNotClaimSuccessOrAlterLastSelection() {
        prefs.edit().putString("presets_json", "damaged snapshot").commit()
        val selection = prefs.all["last_preset_name"]
        assertFalse(controller.saveCurrentConfigToSelectedPreset())
        assertEquals("damaged snapshot", prefs.all["presets_json"])
        assertEquals(selection, prefs.all["last_preset_name"])
        assertTrue(controller.getPresets().isEmpty())
        assertEquals(0, appliedCount)
    }

    @Test fun rejectedArtworkReplacementKeepsThePreviousImageAndDoesNotReturnAnUnsavedPreset() {
        val imageName = PresetImageStorage.importIconFromBytes(activity, "original.png", byteArrayOf(1, 2, 3))!!
        assertNotNull(controller.updatePresetVisual(0) { it.copy(customImageFileName = imageName) })
        prefs.edit().putInt("presets_json", 42).commit()
        assertNull(controller.updatePresetVisual(0) { it.copy(customImageFileName = null) })
        assertEquals(42, prefs.all["presets_json"])
        PresetImageStorage.openIconInputStream(activity, imageName).use { input ->
            assertNotNull("Rejected replacement must not remove saved artwork", input)
            assertArrayEquals(byteArrayOf(1, 2, 3), input!!.readBytes())
        }
        PresetImageStorage.deleteIfExists(activity, imageName)
    }

    @Test fun acceptedEditorSaveReturnsSuccessAndPersistsOnlyTheEditedConfiguration() {
        assertTrue(controller.saveCurrentConfigToSelectedPreset())
        val result = PresetRepository(prefs).list().single()
        assertEquals(saved.id, result.id)
        assertEquals(saved.name, result.name)
        assertEquals(99, result.brightness)
        assertEquals(result, controller.getPresets().single())
        assertEquals(0, appliedCount)
    }

    @Test fun wrongTypedLastSelectionDoesNotCrashLoadingOrReplaceTheLibrary() {
        val raw = prefs.getString("presets_json", null)
        prefs.edit().putInt("last_preset_name", 42).commit()
        assertEquals(saved.id, controller.init(saved).id)
        assertEquals(raw, prefs.getString("presets_json", null))
    }

    @Test fun refusedRenameDoesNotApplyLightingOrRenameAssignments() {
        assertTrue(modifyButton.performClick())
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<TextInputEditText>(R.id.presetNameInput).setText("Renamed")
        prefs.edit().putString("presets_json", "damaged snapshot").commit()
        confirmRename(dialog)
        assertEquals("damaged snapshot", prefs.all["presets_json"])
        assertEquals("Ocean", prefs.all["last_preset_name"])
        assertTrue("Refused save must execute and reload the damaged library", controller.getPresets().isEmpty())
        assertEquals(0, renamedCount)
        assertEquals(0, appliedCount)
        assertEquals("Only the initial editor load should apply UI values", 1, uiAppliedCount)
    }

    @Test fun refusedDeleteAllKeepsOrganizationSelectionAndUndoWithoutCreatingADefaultPreset() {
        val library = PresetLibraryStore(prefs)
        assertTrue(library.setFavorite(saved.id, true))
        val collection = library.createCollection("Evening")!!
        assertTrue(library.setMembership(saved.id, collection.id, true))
        prefs.edit().putString("presets_json", "damaged snapshot")
            .putString(PresetUndoStore.PREF_KEY_UNDO, "existing snapshot").commit()
        val before = prefs.all.toMap()
        PresetController::class.java.getDeclaredMethod("deleteAllPresets").apply {
            isAccessible = true
        }.invoke(controller)
        assertEquals(before, prefs.all)
        assertTrue(controller.getPresets().isEmpty())
        assertEquals(0, appliedCount)
        assertEquals(1, uiAppliedCount)
    }

    @Test fun successfulRenameUpdatesAssignmentsBeforeRefreshingTheUiOrApplyingLighting() {
        profileManager.setMapping("com.example.game", saved.name)
        profileManager.setGameScenePreset(saved.name)
        profileManager.isGameSceneEnabled = true
        callbacks.clear()
        assertTrue(modifyButton.performClick())
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<TextInputEditText>(R.id.presetNameInput).setText("Renamed")
        confirmRename(dialog)
        assertEquals(listOf("renamed", "ui", "applied"), callbacks)
        assertEquals("Renamed", PresetRepository(prefs).list().single().name)
        assertEquals("Renamed", profileManager.gameScenePresetName)
        assertTrue(profileManager.isGameSceneEnabled)
        assertEquals(mapOf("com.example.game" to "Renamed"), profileManager.getMappings())
    }

    @Test fun removingAnInvalidArtworkReferenceSavesWithoutCrashingOrDeletingOutsideTheArtworkDirectory() {
        val outside = File(activity.filesDir, "outside.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            assertNotNull(controller.updatePresetVisual(0) { it.copy(customImageFileName = "../outside.png") })
            assertNotNull(controller.updatePresetVisual(0) { it.copy(customImageFileName = null) })
            assertNull(PresetRepository(prefs).list().single().customImageFileName)
            assertArrayEquals(byteArrayOf(1, 2, 3), outside.readBytes())
        } finally { outside.delete() }
    }

    private fun confirmRename(dialog: AlertDialog) {
        assertTrue("Preset editor should be visible", dialog.isShowing)
        val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertEquals("Update", button.text.toString())
        assertTrue("Update must have an actionable click listener", button.performClick())
        // AlertController dispatches the confirmation through its main-thread
        // ButtonHandler. A click alone would leave the actual save unexecuted
        // under Robolectric's paused looper and make refusal checks meaningless.
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Confirmation should finish the dialog", dialog.isShowing)
    }
}

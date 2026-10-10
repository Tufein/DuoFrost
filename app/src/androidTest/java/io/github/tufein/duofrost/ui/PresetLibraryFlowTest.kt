package io.github.tufein.duofrost.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.MainActivity
import io.github.tufein.duofrost.PresetImageStorage
import io.github.tufein.duofrost.PresetArchiveTransfer
import io.github.tufein.duofrost.PresetLibraryStore
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.PresetUndoStore
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.tools.LedOutputLimits
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Real browsing/restore flows, restricted to the disposable debug application. */
@RunWith(AndroidJUnit4::class)
class PresetLibraryFlowTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var artwork: ByteArray

    @Before fun launchStoppedDebugLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug"))
        assertFalse("Library fixtures begin with lighting stopped", LEDService.isRunning)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
            .edit().clear().commit())
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear()
            .putBoolean("startup_guide_done", true)
            .putBoolean("first_launch_alert_shown", true)
            .putBoolean("plugin_update_prompt_shown", true)
            .putBoolean("plugin_check_updates_at_launch", false)
            .putBoolean("app_profile_info_shown", true)
            .putBoolean("auto_switch_enabled", false)
            .putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putInt(LedOutputLimits.PREF_MAXIMUM, 100)
            .putString("presets_json", fixturePresets().toString())
            .putString("last_preset_name", "Default").commit())
        artwork = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(-65_536)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
        assertTrue(PresetImageStorage.writeIconWithExactName(context, ARTWORK_NAME, artwork))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Default")))
    }

    @After fun closeAndRemoveOwnArtwork() {
        if (::scenario.isInitialized) scenario.close()
        if (::context.isInitialized) PresetImageStorage.deleteIfExists(context, ARTWORK_NAME)
        if (::context.isInitialized) File(context.cacheDir, ARCHIVE_NAME).delete()
        assertFalse("Library actions must leave lighting stopped", LEDService.isRunning)
        if (::context.isInitialized) assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    @Test fun favouriteFilterKeepsOriginalIndexAndSelectsTheActualPreset() {
        val before = prefs.getString("presets_json", null)
        search("Cozy")
        options(COZY_ID).perform(scrollTo(), click())
        onView(withText(R.string.library_add_favorite)).perform(click())
        assertTrue(PresetLibraryStore(prefs).isFavorite(COZY_ID))
        assertEquals("Organising must not apply a preset", "Default", prefs.getString("last_preset_name", null))
        filter(R.string.library_favorites).perform(scrollTo(), click())
        search("")
        assertOnlyCardAtStorageIndex(4)
        card(4).perform(scrollTo(), click())
        assertEquals("Cozy night", prefs.getString("last_preset_name", null))
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Cozy night")))
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<Spinner>(R.id.presetSpinner)
            assertEquals(4, spinner.selectedItemPosition)
            assertEquals(COZY_ID, (spinner.selectedItem as LedPreset).id)
        }
        assertEquals("Filtering and selection must preserve stored lighting", before, prefs.getString("presets_json", null))
        assertFalse(LEDService.isRunning)
    }

    @Test fun creatingAndChoosingCollectionFiltersWithoutChangingLightingSelection() {
        val before = prefs.getString("presets_json", null)
        onView(withId(R.id.gui_libraryCollections)).perform(scrollTo(), click())
        onView(withText(R.string.library_new_collection)).perform(click())
        onView(withId(R.id.library_collection_name_input)).perform(replaceText("Retro games"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())
        search("Cozy")
        options(COZY_ID).perform(scrollTo(), click())
        onView(withText(R.string.library_memberships)).perform(click())
        onView(withText("Retro games")).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        search("")
        onView(allOf(withText("Retro games"), isDescendantOfA(withId(R.id.gui_libraryFilters))))
            .perform(scrollTo(), click())
        assertOnlyCardAtStorageIndex(4)
        val collection = PresetLibraryStore(prefs).state().collections.single()
        assertEquals(setOf(COZY_ID), collection.presetIds)
        assertEquals("Default", prefs.getString("last_preset_name", null))
        assertEquals(before, prefs.getString("presets_json", null))
        assertFalse(LEDService.isRunning)
    }

    @Test fun previewOfLiveInputEffectLeavesPreferencesAndLightingUntouched() {
        search("Ocean")
        options("library-ocean").perform(scrollTo(), click())
        val before = prefs.all.toMap()
        onView(withText(R.string.library_preview)).perform(click())
        onView(withId(R.id.preview_sticks)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText(R.string.preview_live_input)).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals("Preview must not edit any saved setting", before, prefs.all.toMap())
        assertFalse("Preview must not start the LED service", LEDService.isRunning)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
        onView(withText(R.string.gui_back_to_lighting)).perform(scrollTo(), click())
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Default")))
        assertEquals(before, prefs.all.toMap())
    }

    @Test fun deletingLastPresetOffersUndoAndRestoresArtworkAndSpinner() {
        scenario.close()
        val one = JSONArray().put(preset("library-artwork", "Saved artwork", "STATIC")
            .put("customImageFileName", ARTWORK_NAME).put("isAppProfileDefault", true)).toString()
        assertTrue(prefs.edit().putString("presets_json", one)
            .putString("last_preset_name", "Saved artwork").commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val before = prefs.getString("presets_json", null)
        onView(withId(R.id.gui_editLightingButton)).perform(scrollTo(), click())
        waitForEditor(true)
        onView(withId(R.id.deletePresetButton)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).check(matches(withText(R.string.action_delete))).perform(click())
        assertTrue(PresetRepository(prefs).list().isEmpty())
        scenario.onActivity { activity ->
            assertEquals(0, activity.findViewById<Spinner>(R.id.presetSpinner).count)
        }
        assertArtworkRetained()
        onView(withId(R.id.closeSettingsButton)).perform(click())
        waitForEditor(false)
        onView(withId(R.id.gui_libraryUndo)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
        assertEquals(before, prefs.getString("presets_json", null))
        assertEquals(ARTWORK_NAME, PresetRepository(prefs).list().single().customImageFileName)
        assertArtworkRetained()
        assertOnlyCardAtStorageIndex(0)
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<Spinner>(R.id.presetSpinner)
            assertEquals("Undo must repopulate the editor spinner", 1, spinner.count)
            assertEquals("library-artwork", (spinner.selectedItem as LedPreset).id)
        }
        assertFalse(LEDService.isRunning)
    }

    @Test fun showingMorePresetsKeepsStorageIndicesAndSelectionCorrect() {
        scenario.close()
        val largerLibrary = JSONArray().apply {
            repeat(45) { index -> put(preset("paged-$index", "Paged $index", "STATIC")
                .put("isAppProfileDefault", index == 0)) }
        }.toString()
        assertTrue(prefs.edit().putString("presets_json", largerLibrary)
            .putString("last_preset_name", "Paged 0").commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.activePresetNameText)).check(matches(withText("Paged 0")))
        scenario.onActivity { activity ->
            assertEquals(40, activity.findViewById<LinearLayout>(R.id.presetCoverFlowContainer).childCount)
        }
        onView(withId(R.id.gui_libraryMore)).perform(scrollTo(), click())
        scenario.onActivity { activity ->
            val cards = activity.findViewById<LinearLayout>(R.id.presetCoverFlowContainer)
            assertEquals(45, cards.childCount)
            assertEquals(44, cards.getChildAt(44).tag)
        }
        assertEquals("Paged 0", prefs.getString("last_preset_name", null))
        search("Paged 44")
        assertOnlyCardAtStorageIndex(44)
        card(44).perform(scrollTo(), click())
        assertEquals("Paged 44", prefs.getString("last_preset_name", null))
        assertEquals(largerLibrary, prefs.getString("presets_json", null))
        assertFalse(LEDService.isRunning)
    }

    @Test fun damagedAssignmentsBlockOptInImportBeforeAnyPresetIsSaved() {
        seedOrganisedLibraryAndAssignments()
        val beforePresets = prefs.getString("presets_json", null)
        val beforeLibrary = prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null)
        val uri = createCommunityBundle()
        assertTrue(prefs.edit().putInt("app_profile_mappings", 7).commit())
        openMainImport(uri)
        onView(withId(R.id.import_review_mappings)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).perform(click())
        assertEquals(beforePresets, prefs.getString("presets_json", null))
        assertEquals(beforeLibrary, prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null))
        assertEquals(7, prefs.all["app_profile_mappings"])
        assertFalse(prefs.contains(PresetUndoStore.PREF_KEY_UNDO))
        assertFalse(LEDService.isRunning)
    }

    @Test fun mainImportWithOptInAssignmentsCanUndoTheCompleteLibraryChange() {
        val before = seedOrganisedLibraryAndAssignments()
        val uri = createCommunityBundle()
        openMainImport(uri)
        onView(withId(R.id.import_review_mappings)).perform(scrollTo())
            .check(matches(isNotChecked())).perform(click())
        onView(withId(android.R.id.button1)).check(matches(withText(R.string.import_review_apply))).perform(click())

        val importedLibrary = PresetRepository(prefs).list()
        assertEquals(7, importedLibrary.size)
        val copy = importedLibrary.single { it.name == "Ocean (2)" }
        assertNotEquals("Copies must retain a separate identity", "library-ocean", copy.id)
        assertNotEquals("Copies must receive a new local identity", "archive-ocean", copy.id)
        val mappings = JSONObject(prefs.getString("app_profile_mappings", "{}"))
        assertEquals("Default", mappings.getString("com.example.original"))
        assertEquals("Ocean (2)", mappings.getString(context.packageName))
        assertEquals("Import must preserve existing organisation", before.libraryJson,
            prefs.getString(PresetLibraryStore.PREF_KEY_LIBRARY, null))
        assertEquals(PresetUndoStore.Action.IMPORT, PresetUndoStore(prefs).pending()?.action)
        assertFalse(LEDService.isRunning)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))

        onView(withId(R.id.gui_libraryUndo)).perform(scrollTo())
            .check(matches(withText(R.string.library_undo_import))).perform(click())
        assertEquals(before, PresetUndoStore(prefs).captureSnapshot())
        assertEquals(6, PresetRepository(prefs).list().size)
        assertNull(PresetUndoStore(prefs).pending())
        scenario.onActivity { activity ->
            assertEquals("Undo must refresh the actual editor's entries", 6,
                activity.findViewById<Spinner>(R.id.presetSpinner).count)
        }
        assertEquals("Default", prefs.getString("last_preset_name", null))
        assertFalse(LEDService.isRunning)
    }

    @Test fun cancellingMainImportPreservesPresetsOrganisationAndAssignments() {
        val before = seedOrganisedLibraryAndAssignments()
        val uri = createCommunityBundle()
        openMainImport(uri)
        onView(withId(R.id.import_review_mappings)).perform(scrollTo())
            .check(matches(isNotChecked())).perform(click())
        onView(withId(android.R.id.button2)).check(matches(withText(R.string.action_cancel))).perform(click())
        assertEquals(before, PresetUndoStore(prefs).captureSnapshot())
        assertNull("Cancellation must not replace an undo record", PresetUndoStore(prefs).pending())
        assertEquals(6, PresetRepository(prefs).list().size)
        assertEquals("Default", prefs.getString("last_preset_name", null))
        assertFalse(LEDService.isRunning)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    private fun seedOrganisedLibraryAndAssignments(): PresetUndoStore.Snapshot {
        val library = PresetLibraryStore(prefs)
        assertTrue(library.setFavorite(COZY_ID, true))
        val collection = library.createCollection("Existing favourites")
        assertNotNull(collection)
        assertTrue(library.setMembership(COZY_ID, collection!!.id, true))
        assertTrue(prefs.edit().putString("app_profile_mappings", JSONObject()
            .put("com.example.original", "Default").toString()).commit())
        return PresetUndoStore(prefs).captureSnapshot()!!
    }

    private fun createCommunityBundle(): Uri {
        val original = PresetRepository(prefs).list().single { it.id == "library-ocean" }
        val imported = original.copy(id = "archive-ocean", color = -16_776_961)
        val uri = Uri.fromFile(File(context.cacheDir, ARCHIVE_NAME))
        val result = PresetArchiveTransfer.exportToUri(context, uri, listOf(imported),
            mapOf(context.packageName to "Ocean"))
        assertEquals(1, result.presetCount)
        assertTrue(result.warnings.isEmpty())
        return uri
    }

    private fun openMainImport(uri: Uri) {
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("importCommunityPresetBundle", Uri::class.java)
                .apply { isAccessible = true }.invoke(activity, uri)
        }
        onView(withText(R.string.import_review_title)).check(matches(isDisplayed()))
    }

    private fun assertArtworkRetained() {
        val stream = PresetImageStorage.openIconInputStream(context, ARTWORK_NAME)
        assertNotNull("Undo must retain the deleted preset's artwork", stream)
        stream!!.use { assertArrayEquals(artwork, it.readBytes()) }
        val bitmap = PresetImageStorage.loadBitmap(context, ARTWORK_NAME, 48)
        assertNotNull("The restored artwork must remain decodable", bitmap)
        bitmap?.recycle()
    }

    private fun assertOnlyCardAtStorageIndex(index: Int) {
        scenario.onActivity { activity ->
            val cards = activity.findViewById<LinearLayout>(R.id.presetCoverFlowContainer)
            assertEquals(1, cards.childCount)
            assertEquals("A filtered card must retain its original storage index", index, cards.getChildAt(0).tag)
        }
    }

    private fun search(query: String) = onView(withId(R.id.gui_presetSearch))
        .perform(scrollTo(), replaceText(query), closeSoftKeyboard())

    private fun options(id: String) = onView(allOf(withTagValue(equalTo<Any>("preset-options-$id")),
        isDescendantOfA(withId(R.id.presetCoverFlowContainer))))

    private fun card(index: Int) = onView(allOf(withTagValue(equalTo<Any>(index)),
        isDescendantOfA(withId(R.id.presetCoverFlowContainer))))

    private fun filter(label: Int) = onView(allOf(withText(label), isDescendantOfA(withId(R.id.gui_libraryFilters))))

    private fun waitForEditor(open: Boolean) {
        onView(withId(R.id.gui_root)).perform(object : ViewAction {
            override fun getDescription() = "Wait for library editor to settle ${if (open) "open" else "closed"}"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun perform(controller: UiController, root: View) {
                val deadline = SystemClock.uptimeMillis() + 3_000L
                do {
                    controller.loopMainThreadUntilIdle()
                    val overlay = root.findViewById<View>(R.id.settingsOverlay)
                    val settled = if (open) overlay.visibility == View.VISIBLE && overlay.alpha >= 0.99f &&
                        kotlin.math.abs(overlay.translationX) < 1f else overlay.visibility == View.GONE
                    if (settled) return
                    controller.loopMainThreadForAtLeast(50L)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Editor did not settle ${if (open) "open" else "closed"}")
            }
        })
    }

    private fun fixturePresets() = JSONArray().apply {
        val ids = arrayOf("library-default", "library-ocean", "library-sunset", "library-music", COZY_ID, "library-arcade")
        val names = arrayOf("Default", "Ocean", "Sunset", "Music", "Cozy night", "Arcade")
        ids.indices.forEach { index ->
            put(preset(ids[index], names[index], if (index == 1) "AMBIENT" else "STATIC")
                .put("isAppProfileDefault", index == 0))
        }
    }

    private fun preset(id: String, name: String, effect: String) = JSONObject()
        .put("id", id).put("name", name).put("animationType", effect).put("performanceProfile", "HIGH")
        .put("color", -16_711_681).put("rightColor", -65_536).put("brightness", 128)
        .put("speed", 0.5).put("smoothness", 0.5)

    private companion object {
        const val COZY_ID = "library-cozy"
        const val ARTWORK_NAME = "library-flow-test.png"
        const val ARCHIVE_NAME = "library-flow-import.zip"
    }
}

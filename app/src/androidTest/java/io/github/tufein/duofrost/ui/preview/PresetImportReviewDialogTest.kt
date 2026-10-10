package io.github.tufein.duofrost.ui.preview

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetArchiveTransfer
import io.github.tufein.duofrost.PresetCodec
import io.github.tufein.duofrost.PresetImportPlan
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import io.github.tufein.duofrost.tools.PerformanceProfile
import io.github.tufein.duofrost.ui.PresetImportReviewDialog
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetImportReviewDialogTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var before: Map<String, *>
    private lateinit var scenario: ActivityScenario<PresetPreviewActivity>
    private var dialog: AlertDialog? = null
    private var accepted: PresetImportPlan.Plan? = null
    private val current = LedPreset("Ocean", LedAnimationType.STATIC, PerformanceProfile.HIGH,
        color = -1, brightness = 128, speed = 0.5f, smoothness = 0.5f, id = "review-existing")
    private val result = PresetArchiveTransfer.ImportResult(listOf(current.copy(id = "import-one"),
        current.copy(name = "Sunset", id = "import-two")), mapOf("app.ocean" to "Ocean"), emptyList(), emptyList())

    @Before fun showReviewWithLightingStopped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug"))
        assertFalse(LEDService.isRunning)
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putString("presets_json", JSONArray().put(PresetCodec.encode(current)).toString()).commit()
        before = prefs.all.toMap()
        scenario = ActivityScenario.launch(Intent(context, PresetPreviewActivity::class.java)
            .putExtra(PresetPreviewActivity.EXTRA_PRESET_ID, current.id))
        scenario.onActivity { activity ->
            dialog = PresetImportReviewDialog.show(activity, result, listOf(current)) { accepted = it }
        }
        assertNotNull(dialog)
    }

    @After fun cleanupAndVerifyReviewDoesNotSave() {
        scenario.onActivity { dialog?.dismiss() }
        scenario.close()
        assertEquals(before, prefs.all)
        assertFalse(LEDService.isRunning)
    }

    @Test fun cancelDoesNotApplyAnything() {
        scenario.onActivity { dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        assertNull(accepted)
    }

    @Test fun applyReturnsOnlyChosenCopiesAndAppAssignmentsAreOffByDefault() {
        scenario.onActivity {
            val second = dialog!!.window!!.decorView.findViewWithTag<MaterialCheckBox>("import-review-1")
            second.isChecked = false
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        val plan = accepted!!
        assertEquals(1, plan.importCount)
        assertEquals(listOf("Ocean", "Ocean (2)"), plan.finalPresets.map { it.name })
        assertTrueEmptyMappings(plan)
    }

    private fun assertTrueEmptyMappings(plan: PresetImportPlan.Plan) {
        assertEquals(emptyMap<String, String>(), plan.mappings)
        assertFalse(plan.includeMappings)
    }
}

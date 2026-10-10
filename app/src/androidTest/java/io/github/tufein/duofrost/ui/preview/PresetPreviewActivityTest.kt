package io.github.tufein.duofrost.ui.preview

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tufein.duofrost.BuildConfig
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.services.ServiceRecoveryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetPreviewActivityTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private var scenario: ActivityScenario<PresetPreviewActivity>? = null
    private lateinit var before: Map<String, *>
    private lateinit var recoveryPrefs: SharedPreferences
    private lateinit var recoveryBefore: Map<String, *>

    @Before fun setupStoppedLighting() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName.endsWith(".debug"))
        assertFalse("Preview test begins with lighting stopped", LEDService.isRunning)
        prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(ServiceRecoveryStore.PREF_KEEP_RUNNING, false)
            .putString("presets_json", """[{"id":"preview-test","name":"Ocean","animationType":"RAINBOW",
                "brightness":128},{"name":"Legacy without ID","animationType":"STATIC"}]""").commit()
        before = prefs.all.toMap()
        recoveryPrefs = context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE)
        recoveryBefore = recoveryPrefs.all.toMap()
    }

    @After fun assertPreviewHasNoSideEffects() {
        scenario?.close()
        assertEquals("Preview must not migrate or edit any setting", before, prefs.all)
        assertEquals("Preview must not alter desired lighting or recovery", recoveryBefore, recoveryPrefs.all)
        assertFalse("Preview must never start the LED service", LEDService.isRunning)
    }

    @Test fun animatedPreviewCanPauseAndSurvivesRecreationWithoutStartingLighting() {
        var pausedByTest = false
        scenario = ActivityScenario.launch(Intent(context, PresetPreviewActivity::class.java)
            .putExtra(PresetPreviewActivity.EXTRA_PRESET_ID, "preview-test"))
        scenario!!.onActivity { activity ->
            assertNotNull(activity.findViewById<View>(R.id.preview_sticks))
            val button = activity.findViewById<View>(R.id.preview_motion)
            // Animator scale can be disabled by the test device. Both paths remain read only.
            if (button.visibility == View.VISIBLE) {
                button.performClick()
                pausedByTest = true
                assertEquals(activity.getString(R.string.preview_paused),
                    activity.findViewById<TextView>(R.id.preview_status).text.toString())
            }
            assertFalse(LEDService.isRunning)
        }
        scenario!!.recreate()
        scenario!!.onActivity { activity ->
            assertNotNull(activity.findViewById<View>(R.id.preview_sticks))
            if (pausedByTest) {
                assertEquals(activity.getString(R.string.preview_paused),
                    activity.findViewById<TextView>(R.id.preview_status).text.toString())
            }
            assertFalse(LEDService.isRunning)
        }
    }

    @Test fun unavailablePresetShowsAnEmptyStateWithoutMigratingLegacyPresets() {
        scenario = ActivityScenario.launch(Intent(context, PresetPreviewActivity::class.java)
            .putExtra(PresetPreviewActivity.EXTRA_PRESET_ID, "missing"))
        scenario!!.onActivity { activity ->
            assertNull(activity.findViewById<View>(R.id.preview_sticks))
            assertFalse(LEDService.isRunning)
        }
    }

    @Test fun wronglyTypedSavedDataShowsAnEmptyStateAndStaysIntact() {
        prefs.edit().putInt("presets_json", 42).commit()
        before = prefs.all.toMap()
        scenario = ActivityScenario.launch(Intent(context, PresetPreviewActivity::class.java)
            .putExtra(PresetPreviewActivity.EXTRA_PRESET_ID, "preview-test"))
        scenario!!.onActivity { activity ->
            assertNull(activity.findViewById<View>(R.id.preview_sticks))
            assertFalse(LEDService.isRunning)
        }
    }
}

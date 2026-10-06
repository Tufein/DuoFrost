package io.github.tufein.duofrost

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Inflate and measure the real layouts to catch clipped settings and duplicate control IDs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LandscapeLayoutTest {
    @Test
    @Config(qualifiers = "w800dp-h320dp-land-mdpi")
    fun shortLandscapeLeavesRoomToScrollSettings() {
        val root = inflateHome(800, 320)
        assertUniqueControlIds(root)
        assertTrue("Settings need room for at least two control rows", settingsScrollHeight(root, 800, 320) >= 96)
    }

    @Test
    @Config(qualifiers = "w640dp-h280dp-land-mdpi")
    fun shorterLandscapeKeepsSettingsScrollable() {
        val root = inflateHome(640, 280)
        assertUniqueControlIds(root)
        assertTrue("Settings need room for at least one control row", settingsScrollHeight(root, 640, 280) >= 48)
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-port-mdpi")
    fun portraitRetainsAllControls() {
        val root = inflateHome(400, 800)
        assertUniqueControlIds(root)
        assertTrue(settingsScrollHeight(root, 400, 800) >= 400)
    }

    private fun inflateHome(width: Int, height: Int): View {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuoFrost)
        return LayoutInflater.from(context).inflate(R.layout.activity_main, null).also {
            measure(it, width, height)
        }
    }

    private fun settingsScrollHeight(root: View, width: Int, height: Int): Int {
        root.findViewById<View>(R.id.settingsOverlay).visibility = View.VISIBLE
        measure(root, width, height)
        return root.findViewById<ScrollView>(R.id.mainSettingsScroll).height
    }

    private fun measure(root: View, width: Int, height: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
    }

    private fun assertUniqueControlIds(root: View) {
        val ids = mutableSetOf<Int>()
        fun visit(view: View) {
            if (view.id != View.NO_ID) assertTrue("Duplicate view ID ${view.id}", ids.add(view.id))
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
        for (id in listOf(R.id.serviceToggle, R.id.homeAppProfileSwitch, R.id.homeSettingsButton,
            R.id.presetCoverFlowScroll, R.id.presetCoverFlowContainer, R.id.activePresetInfoCard,
            R.id.gui_presetSearch, R.id.presetSpinner, R.id.exportPresetsButton, R.id.importPresetsButton,
            R.id.tabUiSettings, R.id.tabBehaviorSettings, R.id.tabThemesSettings,
            R.id.liveWallpaperTabButton, R.id.closeSettingsButton)) {
            assertTrue("Missing control $id", ids.contains(id))
        }
    }
}

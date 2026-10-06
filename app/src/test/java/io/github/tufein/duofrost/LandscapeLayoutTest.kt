package io.github.tufein.duofrost

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import io.github.tufein.duofrost.ui.LockableHorizontalScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Inflate and measure the real layouts to catch clipping and duplicate control IDs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LandscapeLayoutTest {
    @Test
    @Config(qualifiers = "w800dp-h320dp-land-mdpi")
    fun landscapeKeepsPresetCardsAndDetailsVisible() {
        val root = inflateHome(800, 320)
        val carousel = root.findViewById<LockableHorizontalScrollView>(R.id.presetCoverFlowScroll)
        val details = root.findViewById<View>(R.id.activePresetInfoCard)
        val cards = root.findViewById<ViewGroup>(R.id.presetCoverFlowContainer)
        val tileSize = root.resources.getDimensionPixelSize(R.dimen.bifrost_cover_flow_tile_size)
        cards.addView(View(root.context), ViewGroup.LayoutParams(tileSize, tileSize))
        measure(root, 800, 320)
        assertTrue("Preset cards must fit without vertical clipping", carousel.height >= tileSize)
        assertTrue("Details should use the space beside the carousel", details.parent !== carousel.parent)
        assertTrue(carousel.width > tileSize)
        assertUniqueControlIds(root)

        root.findViewById<View>(R.id.settingsOverlay).visibility = View.VISIBLE
        measure(root, 800, 320)
        val scroll = root.findViewById<ScrollView>(R.id.mainSettingsScroll)
        assertTrue("Compact landscape navigation should leave room to scroll", scroll.height >= 160)
    }

    @Test
    @Config(qualifiers = "w640dp-h280dp-land-mdpi")
    fun shortLandscapeStillFitsThePresetTile() {
        val root = inflateHome(640, 280)
        val carousel = root.findViewById<View>(R.id.presetCoverFlowScroll)
        assertTrue(carousel.height >= root.resources.getDimensionPixelSize(R.dimen.bifrost_cover_flow_tile_size))
        assertUniqueControlIds(root)
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-port-mdpi")
    fun portraitRetainsStackedHomeAndAllControls() {
        val root = inflateHome(400, 800)
        val carousel = root.findViewById<View>(R.id.presetCoverFlowScroll)
        val details = root.findViewById<View>(R.id.activePresetInfoCard)
        assertTrue(details.top >= carousel.bottom)
        assertEquals(176, root.resources.getDimensionPixelSize(R.dimen.bifrost_cover_flow_tile_size))
        assertUniqueControlIds(root)
    }

    private fun inflateHome(width: Int, height: Int): View {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuoFrost)
        return LayoutInflater.from(context).inflate(R.layout.activity_main, null).also {
            measure(it, width, height)
        }
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
            R.id.presetSpinner, R.id.exportPresetsButton, R.id.importPresetsButton,
            R.id.tabUiSettings, R.id.tabBehaviorSettings, R.id.tabThemesSettings,
            R.id.liveWallpaperTabButton, R.id.closeSettingsButton)) {
            assertTrue("Missing control $id", ids.contains(id))
        }
    }
}

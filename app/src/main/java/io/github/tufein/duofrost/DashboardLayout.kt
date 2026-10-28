package io.github.tufein.duofrost

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

object DashboardLayout {
    val options = linkedMapOf(
        "dashboard_library_first" to "Preset library above lighting controls",
        "dashboard_show_library" to "Show preset library",
        "dashboard_show_scenes" to "Show Smart Scenes shortcut",
        "dashboard_show_output" to "Show brightness controls",
        "dashboard_show_quick" to "Show mute and sleep timer",
        "dashboard_show_profiles" to "Show app profiles switch"
    )
    fun default(key: String) = key != "dashboard_library_first"
    fun apply(activity: AppCompatActivity, prefs: SharedPreferences) {
        fun show(key: String, view: View) {
            view.visibility = if (prefs.getBoolean(key, default(key))) View.VISIBLE else View.GONE
        }
        val content = activity.findViewById<ViewGroup>(R.id.gui_homeContent)
        val controls = activity.findViewById<View>(R.id.activePresetInfoCard)
        val library = activity.findViewById<View>(R.id.gui_libraryCard)
        val first = if (prefs.getBoolean("dashboard_library_first", false)) library else controls
        if (content.getChildAt(0) !== first) {
            content.removeView(first)
            content.addView(first, 0)
        }
        show("dashboard_show_library", library)
        show("dashboard_show_scenes", activity.findViewById(R.id.gui_scenesButton))
        show("dashboard_show_quick", activity.findViewById<View>(R.id.gui_muteButton).parent as View)
        show("dashboard_show_quick", activity.findViewById(R.id.gui_timerStatus))
        show("dashboard_show_profiles", activity.findViewById(R.id.homeAppProfileSwitch))
        val slider = activity.findViewById<View>(R.id.gui_outputSeekBar)
        val parent = slider.parent as ViewGroup
        val index = parent.indexOfChild(slider)
        for (i in index - 1..index + 1) show("dashboard_show_output", parent.getChildAt(i))
    }
}

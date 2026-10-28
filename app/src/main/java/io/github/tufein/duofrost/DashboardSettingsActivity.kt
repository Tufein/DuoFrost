package io.github.tufein.duofrost

import android.os.Bundle
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import io.github.tufein.duofrost.ui.ThemedActivity

class DashboardSettingsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
        val (root, content) = SecondaryScreenUi.screen(this, "Dashboard layout",
            "Choose your card order and hide controls you do not use. Lighting status and Start/Stop always remain available.")
        for ((key, label) in DashboardLayout.options) {
            content.addView(SecondaryScreenUi.toggle(this, label, prefs.getBoolean(key, DashboardLayout.default(key))) {
                prefs.edit().putBoolean(key, it).apply()
            })
        }
        content.addView(SecondaryScreenUi.action(this, "Restore default layout") {
            val edit = prefs.edit()
            DashboardLayout.options.keys.forEach { edit.remove(it) }
            edit.apply()
            recreate()
        })
        setContentView(root)
    }
}

package io.github.tufein.duofrost

import android.os.Bundle
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import io.github.tufein.duofrost.ui.ThemedActivity
import io.github.tufein.duofrost.ui.ThemedDialogBuilder
import android.widget.Toast

class PresetTransitionsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        val repository = PresetRepository(getSharedPreferences("bifrost_prefs", MODE_PRIVATE))
        val (root, content) = SecondaryScreenUi.screen(this, "Preset transitions",
            "Choose how each incoming preset blends from the previous colors. This changes future switches, not the running effect. Brightness limits and mute still apply.")
        val presets = repository.list()
        if (presets.isEmpty()) content.addView(SecondaryScreenUi.body(this, "Create or import a preset first."))
        presets.forEach { preset ->
            content.addView(SecondaryScreenUi.action(this,
                preset.name + " · " + if (preset.transitionDurationMs == 0) "Instant" else "${preset.transitionDurationMs} ms") {
                val durations = listOf(0, 250, 500, 1000, 2000)
                ThemedDialogBuilder(this).setTitle(preset.name)
                    .setSingleChoiceItems(arrayOf("Instant", "Smooth · 250 ms", "Smooth · 500 ms", "Smooth · 1 second", "Smooth · 2 seconds"),
                        durations.indexOf(preset.transitionDurationMs)) { dialog, index ->
                        val current = repository.list()
                        val updated = current.map { if (it.id == preset.id) it.copy(transitionDurationMs = durations[index]) else it }
                        if (repository.save(updated) == null) Toast.makeText(this, "Preset data could not be saved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        render()
                    }.setNegativeButton("Cancel", null).show()
            })
        }
        setContentView(root)
    }
}

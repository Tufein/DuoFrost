package io.github.tufein.duofrost.ui

import io.github.tufein.duofrost.LedPreset
import java.text.Normalizer
import java.util.Locale

/** A library filter only; returned indices still address the original preset list. */
object PresetLibrarySearch {
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun matchingIndices(presets: List<LedPreset>, query: String): List<Int> {
        val needle = normalize(query)
        if (needle.isEmpty()) return presets.indices.toList()

        return presets.indices.filter { index ->
            val preset = presets[index]
            normalize(preset.name).contains(needle) ||
                normalize(preset.animationType.name).contains(needle)
        }
    }

    private fun normalize(value: String): String = combiningMarks.replace(
        Normalizer.normalize(value, Normalizer.Form.NFD), ""
    ).lowercase(Locale.ROOT).replace('_', ' ').trim().replace(whitespace, " ")
}

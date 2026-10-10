package io.github.tufein.duofrost

/** Pure review plan for a community bundle. Building a plan never saves or applies lighting. */
object PresetImportPlan {
    const val MAX_IMPORTED_PRESETS = 500
    const val MAX_LIBRARY_PRESETS = 2_000

    enum class Mode { ADD_COPIES, REPLACE_MATCHING, SKIP_CONFLICTS }
    enum class Error { TOO_MANY_IMPORTED, LIBRARY_LIMIT, NO_SELECTION, NOTHING_ACCEPTED }
    data class AcceptedPreset(val originalIndex: Int, val originalName: String, val preset: LedPreset,
                              val replaced: Boolean, val protectedCopy: Boolean)
    data class Plan(
        val basePresets: List<LedPreset>,
        val finalPresets: List<LedPreset>,
        val selectedIndices: Set<Int>,
        val mode: Mode,
        val accepted: List<AcceptedPreset>,
        val mappings: Map<String, String>,
        val skippedCount: Int,
        val errors: Set<Error>,
        val includeMappings: Boolean
    ) {
        val importCount: Int get() = accepted.size
        val addedCount: Int get() = accepted.count { !it.replaced }
        val replacedCount: Int get() = accepted.count { it.replaced }
        val protectedCopyCount: Int get() = accepted.count { it.protectedCopy }
        val canApply: Boolean get() = errors.isEmpty() && accepted.isNotEmpty()
    }

    fun build(
        currentPresets: List<LedPreset>,
        importedPresets: List<LedPreset>,
        selectedIndices: Set<Int>,
        mode: Mode = Mode.ADD_COPIES,
        importedMappings: Map<String, String> = emptyMap(),
        includeMappings: Boolean = true
    ): Plan {
        val base = currentPresets.toList()
        val selected = selectedIndices.filterTo(sortedSetOf()) { it in importedPresets.indices }
        fun invalid(error: Error) = Plan(base, base, selected, mode, emptyList(), emptyMap(),
            selected.size, setOf(error), includeMappings)
        if (importedPresets.size > MAX_IMPORTED_PRESETS) return invalid(Error.TOO_MANY_IMPORTED)
        if (base.size > MAX_LIBRARY_PRESETS) return invalid(Error.LIBRARY_LIMIT)
        if (selected.isEmpty()) return invalid(Error.NO_SELECTION)

        val final = base.toMutableList()
        val usedIds = base.mapTo(mutableSetOf()) { it.id }
        val usedNames = base.mapTo(mutableSetOf()) { it.name }
        val baseByName = base.withIndex().groupBy { it.value.name }
        val replacedIndices = mutableSetOf<Int>()
        val accepted = mutableListOf<AcceptedPreset>()
        var skipped = 0
        selected.forEach { index ->
            val imported = importedPresets[index]
            val originalName = imported.name
            val desired = originalName.trim().take(120).ifBlank { "Imported Preset ${index + 1}" }
            val candidates = baseByName[desired].orEmpty()
            val target = candidates.singleOrNull()
            val managedConflict = candidates.any { !it.value.ownerPackage.isNullOrBlank() }
            val hasConflict = desired in usedNames
            if (mode == Mode.SKIP_CONFLICTS && hasConflict) {
                skipped++
                return@forEach
            }
            val replace = mode == Mode.REPLACE_MATCHING && target != null && !managedConflict &&
                PresetIdentity.isValid(target.value.id) && target.index !in replacedIndices
            val resulting = if (replace) {
                replacedIndices += target!!.index
                imported.copy(id = target.value.id, name = target.value.name, ownerPackage = target.value.ownerPackage,
                    isAppProfileDefault = target.value.isAppProfileDefault)
            } else {
                val name = uniqueName(desired, usedNames)
                var id = PresetIdentity.newId()
                while (!usedIds.add(id)) id = PresetIdentity.newId()
                imported.copy(id = id, name = name, ownerPackage = null, isAppProfileDefault = false)
            }
            if (replace) final[target!!.index] = resulting else {
                final += resulting
                usedNames += resulting.name
            }
            accepted += AcceptedPreset(index, originalName, resulting, replace,
                mode == Mode.REPLACE_MATCHING && managedConflict)
        }
        if (final.size > MAX_LIBRARY_PRESETS) return invalid(Error.LIBRARY_LIMIT)
        if (accepted.isEmpty()) return invalid(Error.NOTHING_ACCEPTED)

        // Name-only archive assignments cannot disambiguate duplicate archive entries.
        val sourceNameCounts = importedPresets.groupingBy { it.name }.eachCount()
        val acceptedByName = accepted.groupBy { it.originalName }
        val mappings = if (!includeMappings) emptyMap() else buildMap {
            importedMappings.forEach { (packageName, sourceName) ->
                if (sourceNameCounts[sourceName] != 1) return@forEach
                val target = acceptedByName[sourceName]?.singleOrNull() ?: return@forEach
                put(packageName, target.preset.name)
            }
        }
        return Plan(base, final.toList(), selected, mode, accepted.toList(), mappings, skipped,
            emptySet(), includeMappings)
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        if (base !in used) return base
        var suffix = 2
        while (true) {
            val label = " ($suffix)"
            val candidate = base.take(120 - label.length) + label
            if (candidate !in used) return candidate
            suffix++
        }
    }
}

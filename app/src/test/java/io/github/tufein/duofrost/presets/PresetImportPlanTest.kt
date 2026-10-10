package io.github.tufein.duofrost

import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.PerformanceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetImportPlanTest {
    private fun preset(name: String, id: String, owner: String? = null, default: Boolean = false) =
        LedPreset(name, LedAnimationType.STATIC, PerformanceProfile.HIGH, color = -1,
            brightness = 255, speed = 0.5f, smoothness = 0.5f, id = id,
            ownerPackage = owner, isAppProfileDefault = default)

    @Test fun defaultModeAddsOnlySelectedPresetsWithNewIdentitiesAndUniqueNames() {
        val existing = preset("Ocean", "existing")
        val imported = listOf(preset("Ocean", "imported"), preset("Sunset", "ignored"))
        val plan = PresetImportPlan.build(listOf(existing), imported, setOf(0))
        assertTrue(plan.canApply)
        assertEquals(1, plan.importCount)
        assertEquals(listOf("Ocean", "Ocean (2)"), plan.finalPresets.map { it.name })
        assertEquals(existing, plan.finalPresets.first())
        assertNotEquals("imported", plan.accepted.single().preset.id)
        assertNotEquals("existing", plan.accepted.single().preset.id)
        assertEquals("Ocean", imported.first().name)
    }

    @Test fun replacingMatchingUserPresetPreservesStableIdDefaultAndLibraryOrder() {
        val first = preset("Ocean", "ocean", default = true)
        val second = preset("Sunset", "sunset")
        val imported = preset("Ocean", "untrusted-id", owner = "untrusted.owner").copy(color = 123)
        val plan = PresetImportPlan.build(listOf(first, second), listOf(imported), setOf(0),
            PresetImportPlan.Mode.REPLACE_MATCHING)
        assertEquals("ocean", plan.finalPresets.first().id)
        assertEquals(123, plan.finalPresets.first().color)
        assertTrue(plan.finalPresets.first().isAppProfileDefault)
        assertEquals(null, plan.finalPresets.first().ownerPackage)
        assertEquals(second, plan.finalPresets.last())
        assertEquals(1, plan.replacedCount)
    }

    @Test fun importedDefaultAndOwnershipFlagsCannotTakeOverUserLibrary() {
        val plan = PresetImportPlan.build(emptyList(),
            listOf(preset("New", "id", owner = "com.plugin", default = true)), setOf(0))
        assertEquals(null, plan.accepted.single().preset.ownerPackage)
        assertFalse(plan.accepted.single().preset.isAppProfileDefault)
    }

    @Test fun managedPresetIsKeptAndReplaceModeCreatesAnUnownedCopy() {
        val managed = preset("Plugin", "plugin-id", "com.plugin")
        val plan = PresetImportPlan.build(listOf(managed), listOf(preset("Plugin", "imported")), setOf(0),
            PresetImportPlan.Mode.REPLACE_MATCHING)
        assertEquals(managed, plan.finalPresets.first())
        assertEquals("Plugin (2)", plan.finalPresets.last().name)
        assertEquals(null, plan.finalPresets.last().ownerPackage)
        assertEquals(1, plan.protectedCopyCount)
        assertEquals(0, plan.replacedCount)
    }

    @Test fun skipConflictsAddsOnlyNamesAbsentFromTheLibraryAndEarlierSelections() {
        val imported = listOf(preset("Ocean", "one"), preset("New", "two"), preset("New", "three"))
        val plan = PresetImportPlan.build(listOf(preset("Ocean", "existing")), imported, setOf(0, 1, 2),
            PresetImportPlan.Mode.SKIP_CONFLICTS)
        assertEquals(listOf("Ocean", "New"), plan.finalPresets.map { it.name })
        assertEquals(2, plan.skippedCount)
        assertEquals(1, plan.importCount)
    }

    @Test fun duplicateSelectedNamesCannotReplaceEachOtherOrLoseAnAcceptedEntry() {
        val imported = listOf(preset("Ocean", "one"), preset("Ocean", "two"))
        val plan = PresetImportPlan.build(listOf(preset("Ocean", "existing")), imported, setOf(0, 1),
            PresetImportPlan.Mode.REPLACE_MATCHING)
        assertEquals(listOf("Ocean", "Ocean (2)"), plan.finalPresets.map { it.name })
        assertEquals(2, plan.importCount)
        assertEquals(1, plan.replacedCount)
        assertEquals(1, plan.addedCount)
        assertTrue(plan.accepted.all { it.preset in plan.finalPresets })
    }

    @Test fun ambiguousExistingNameDoesNotOverwriteAnArbitraryPreset() {
        val base = listOf(preset("Ocean", "first"), preset("Ocean", "second"))
        val plan = PresetImportPlan.build(base, listOf(preset("Ocean", "imported")), setOf(0),
            PresetImportPlan.Mode.REPLACE_MATCHING)
        assertEquals(base, plan.finalPresets.take(2))
        assertEquals("Ocean (2)", plan.finalPresets.last().name)
        assertEquals(0, plan.replacedCount)
    }

    @Test fun mappingsFollowSelectedRenamedCopiesAndExcludeUnselectedEntries() {
        val plan = PresetImportPlan.build(listOf(preset("Ocean", "existing")),
            listOf(preset("Ocean", "one"), preset("Sunset", "two")), setOf(0),
            importedMappings = mapOf("app.ocean" to "Ocean", "app.sunset" to "Sunset", "app.missing" to "Missing"))
        assertEquals(mapOf("app.ocean" to "Ocean (2)"), plan.mappings)
    }

    @Test fun duplicateSourceNamesCannotProvideAnUnambiguousAppAssignment() {
        val plan = PresetImportPlan.build(emptyList(),
            listOf(preset("Ocean", "one"), preset("Ocean", "two")), setOf(0),
            importedMappings = mapOf("app.ocean" to "Ocean"))
        assertTrue(plan.mappings.isEmpty())
    }

    @Test fun appAssignmentsCanBeExplicitlyExcluded() {
        val plan = PresetImportPlan.build(emptyList(), listOf(preset("Ocean", "one")), setOf(0),
            importedMappings = mapOf("app.ocean" to "Ocean"), includeMappings = false)
        assertTrue(plan.mappings.isEmpty())
        assertFalse(plan.includeMappings)
    }

    @Test fun reviewAndLibraryBoundsRejectWithoutProducingPartialMutations() {
        val overImportLimit = List(501) { preset("Preset $it", "import-$it") }
        val tooMany = PresetImportPlan.build(emptyList(), overImportLimit, setOf(0))
        assertFalse(tooMany.canApply)
        assertEquals(setOf(PresetImportPlan.Error.TOO_MANY_IMPORTED), tooMany.errors)
        val base = List(2_000) { preset("Existing $it", "existing-$it") }
        val tooLarge = PresetImportPlan.build(base, listOf(preset("New", "new")), setOf(0))
        assertFalse(tooLarge.canApply)
        assertEquals(base, tooLarge.finalPresets)
        assertEquals(setOf(PresetImportPlan.Error.LIBRARY_LIMIT), tooLarge.errors)
        val replacement = PresetImportPlan.build(base, listOf(preset("Existing 0", "new")), setOf(0),
            PresetImportPlan.Mode.REPLACE_MATCHING)
        assertTrue(replacement.canApply)
        assertEquals(2_000, replacement.finalPresets.size)
    }

    @Test fun emptyAndInvalidSelectionCannotSaveAnything() {
        val imported = listOf(preset("Ocean", "one"))
        val plan = PresetImportPlan.build(emptyList(), imported, setOf(-1, 100))
        assertFalse(plan.canApply)
        assertEquals(setOf(PresetImportPlan.Error.NO_SELECTION), plan.errors)
        assertTrue(plan.finalPresets.isEmpty())
    }

    @Test fun skipModeWithOnlyConflictsHasNothingToCommit() {
        val base = listOf(preset("Ocean", "one"))
        val plan = PresetImportPlan.build(base, listOf(preset("Ocean", "two")), setOf(0),
            PresetImportPlan.Mode.SKIP_CONFLICTS)
        assertFalse(plan.canApply)
        assertEquals(base, plan.finalPresets)
        assertEquals(setOf(PresetImportPlan.Error.NOTHING_ACCEPTED), plan.errors)
    }

    @Test fun longNamesAndTrimmedConflictsRemainBoundedAndUnique() {
        val name = "A".repeat(120)
        val plan = PresetImportPlan.build(listOf(preset(name, "existing")),
            listOf(preset("  $name  ", "one")), setOf(0))
        assertEquals(120, plan.finalPresets.last().name.length)
        assertTrue(plan.finalPresets.last().name.endsWith(" (2)"))
        assertEquals(2, plan.finalPresets.map { it.name }.toSet().size)
    }
}

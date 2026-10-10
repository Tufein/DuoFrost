package io.github.tufein.duofrost.services

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppProfileMappingsSafetyTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var manager: AppProfileManager

    @Before fun setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("mapping-safety", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        manager = AppProfileManager(prefs)
    }

    @Test fun wrongTypedMappingDataClearsCachedMappingsAndEveryWriteKeepsItIntact() {
        manager.setMapping("com.example.game", "Ocean")
        assertEquals(mapOf("com.example.game" to "Ocean"), manager.getMappings())
        assertTrue(manager.canEditMappings)
        prefs.edit().putInt(KEY, 42).commit()
        assertFalse(manager.canEditMappings)
        assertTrue(manager.getMappings().isEmpty())
        tryEveryMutation()
        assertEquals(42, prefs.all[KEY])
        assertFalse(manager.canEditMappings)
        assertTrue(manager.getMappings().isEmpty())
        prefs.edit().putString(KEY, "{}").commit()
        assertTrue(manager.canEditMappings)
        manager.setMapping("com.example.other", "Sunset")
        assertEquals(mapOf("com.example.other" to "Sunset"), manager.getMappings())
    }

    @Test fun malformedAndOpaqueMappingFormatsAreNeitherCoercedNorOverwritten() {
        listOf("broken-json", "", "[]", "null", """{"com.example.game":123}""",
            """{"com.example.game":{"preset":"Ocean"}}""", """{"com.example.game":null}""",
            """{"":"Ocean"}""", """{"com.example.game":" "}""",
            """{"com.example.game":"Ocean","future_schema":2}""").forEach { raw ->
            prefs.edit().putString(KEY, raw).commit()
            assertFalse("Mapping format must stay read only: $raw", manager.canEditMappings)
            assertTrue("No partial or coerced mappings: $raw", manager.getMappings().isEmpty())
            tryEveryMutation()
            assertEquals(raw, prefs.all[KEY])
        }
        prefs.edit().remove(KEY).commit()
        assertTrue(manager.canEditMappings)
        manager.replaceMappings(mapOf("com.example.game" to "Ocean"))
        manager.renamePresetInMappings("Ocean", "Ocean renamed")
        assertEquals(mapOf("com.example.game" to "Ocean renamed"), manager.getMappings())
        manager.removeMappingsReferencing(listOf("Ocean renamed"))
        assertTrue(manager.getMappings().isEmpty())
    }

    private fun tryEveryMutation() {
        manager.setMapping("com.example.other", "Sunset")
        manager.removeMapping("com.example.game")
        manager.renamePresetInMappings("Ocean", "Ocean renamed")
        manager.removeMappingsReferencing(listOf("Ocean"))
        manager.replaceMappings(mapOf("com.example.replacement" to "Replacement"))
    }

    companion object { private const val KEY = "app_profile_mappings" }
}

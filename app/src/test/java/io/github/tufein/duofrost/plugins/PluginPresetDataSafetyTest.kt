package io.github.tufein.duofrost.plugins

import io.github.tufein.duofrost.PresetCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginPresetDataSafetyTest {
    @Test fun updateRetainsOpaqueEntriesOtherOwnerAndMatchedIdentityAndUnknownFields() {
        val existing = JSONArray("""[42,"opaque",null,{"id":"other","name":"Retro","ownerPackage":"plugin:other","future":"other data"},{"id":"stable","name":"Retro","ownerPackage":"plugin:retro","future":{"flag":true}}]""")
        val before = existing.toString()
        val imported = PresetCodec.decode(JSONObject("""{"id":"from-bundle","name":"Retro","brightness":150}"""))
        val result = PluginInstaller.replaceOwnedPresets(existing, listOf(imported), "plugin:retro")
        assertEquals(before, existing.toString())
        assertEquals(5, result.length())
        assertEquals(42, result.getInt(0))
        assertEquals("opaque", result.getString(1))
        assertTrue(result.isNull(2))
        assertEquals("other data", result.getJSONObject(3).getString("future"))
        val replaced = result.getJSONObject(4)
        assertEquals("stable", replaced.getString("id"))
        assertEquals("plugin:retro", replaced.getString("ownerPackage"))
        assertEquals(150, replaced.getInt("brightness"))
        assertTrue(replaced.getJSONObject("future").getBoolean("flag"))
    }

    @Test fun ambiguousOldNamesAndReservedIdsNeverRedirectAnotherPresetsIdentity() {
        val existing = JSONArray("""[{"id":"reserved","name":"Other"},{"id":"old-a","name":"Same","ownerPackage":"plugin:p"},{"id":"old-b","name":"Same","ownerPackage":"plugin:p"}]""")
        val imported = PresetCodec.decode(JSONObject("""{"id":"reserved","name":"Same"}"""))
        val result = PluginInstaller.replaceOwnedPresets(existing, listOf(imported, imported.copy(name = "Second")), "plugin:p")
        assertEquals(3, result.length())
        val ids = (0 until result.length()).map { result.getJSONObject(it).getString("id") }
        assertEquals("reserved", ids.first())
        assertEquals(3, ids.toSet().size)
        assertFalse(ids.drop(1).any { it == "old-a" || it == "old-b" || it == "reserved" })
    }
}

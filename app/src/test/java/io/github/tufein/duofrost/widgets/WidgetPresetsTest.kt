package io.github.tufein.duofrost.widgets

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WidgetPresetsTest {
    @Test fun readsUniquePresetNamesInSavedOrder() {
        assertEquals(listOf("Blue", "Rainbow"), WidgetPresets.names(
            """[{"name":"Blue"},{"name":"Rainbow"},{"name":"Blue"}]"""))
    }
    @Test fun brokenMissingOrNonArrayDataIsEmpty() {
        for (raw in listOf(null, "", "broken", "{}", "[")) assertTrue(WidgetPresets.names(raw).isEmpty())
    }
    @Test fun rejectsWrongTypesBlankAndOversizedNames() {
        val array = JSONArray().put(JSONObject().put("name", 5)).put(JSONObject().put("name", " "))
            .put(JSONObject().put("name", "x".repeat(129))).put(JSONObject().put("name", "Valid"))
        assertEquals(listOf("Valid"), WidgetPresets.names(array.toString()))
    }
    @Test fun malformedEntriesDoNotHideFollowingPresets() {
        assertEquals(listOf("Last"), WidgetPresets.names("""[null,7,{},{"name":"Last"}]"""))
    }
    @Test fun inputSizeAndEntryCountAreBounded() {
        assertTrue(WidgetPresets.names(" ".repeat(1_048_577)).isEmpty())
        val array = JSONArray()
        for (index in 0..300) array.put(JSONObject().put("name", "Preset $index"))
        assertEquals(256, WidgetPresets.names(array.toString()).size)
    }
}

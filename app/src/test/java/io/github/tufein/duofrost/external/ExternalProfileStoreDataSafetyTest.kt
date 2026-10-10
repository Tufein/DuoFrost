package io.github.tufein.duofrost.external

import android.content.SharedPreferences
import io.github.tufein.duofrost.animations.LedAnimationType
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ExternalProfileStoreDataSafetyTest {
    private val values = mutableMapOf<String, Any?>()
    private var writeWithoutLock = false
    private val prefs = memoryPreferences()

    @Test fun malformedOrWrongTypedDataCannotBeReplacedByInstallOrRemovedByOwnerCleanup() {
        for (raw in listOf<Any>("invalid", "{}", "", 42)) {
            values["presets_json"] = raw
            assertFalse(ExternalProfileStore.installManagedPreset(prefs, command()))
            assertFalse(ExternalProfileStore.uninstallManagedPreset(prefs, "com.game", "Managed"))
            assertTrue(ExternalProfileStore.removePresetsOwnedBy(prefs, "com.game").isEmpty())
            assertEquals(raw, values["presets_json"])
        }
        assertFalse(writeWithoutLock)
    }

    @Test fun installUpdateAndUninstallPreserveOpaqueEntriesAndAnotherOwnersPreset() {
        values["presets_json"] = """[42,"opaque",null,{"id":"other","name":"Managed","ownerPackage":"com.other","futureArtwork":"retain"}]"""
        assertTrue(ExternalProfileStore.installManagedPreset(prefs, command()))
        val installed = JSONArray(values["presets_json"] as String)
        val managedId = installed.getJSONObject(4).getString("id")
        assertTrue(ExternalProfileStore.installManagedPreset(prefs, command().copy(intensity = 100)))
        val updated = JSONArray(values["presets_json"] as String)
        assertEquals(managedId, updated.getJSONObject(4).getString("id"))
        assertEquals(100, updated.getJSONObject(4).getInt("brightness"))
        assertTrue(ExternalProfileStore.uninstallManagedPreset(prefs, "com.game", "Managed"))
        val restored = JSONArray(values["presets_json"] as String)
        assertEquals(4, restored.length())
        assertEquals(42, restored.getInt(0))
        assertEquals("opaque", restored.getString(1))
        assertTrue(restored.isNull(2))
        assertEquals("retain", restored.getJSONObject(3).getString("futureArtwork"))
        assertEquals("com.other", restored.getJSONObject(3).getString("ownerPackage"))
        assertFalse(writeWithoutLock)
    }

    @Test fun ownerCleanupKeepsOpaqueEntriesAndOriginalRemainingOrder() {
        values["presets_json"] = """["first",{"id":"a","name":"One","ownerPackage":"com.game"},42,{"id":"b","name":"Two","ownerPackage":"com.game"},{"id":"other","name":"Other","ownerPackage":"com.other"}]"""
        assertEquals(listOf("One", "Two"), ExternalProfileStore.removePresetsOwnedBy(prefs, "com.game"))
        val remaining = JSONArray(values["presets_json"] as String)
        assertEquals(3, remaining.length())
        assertEquals("first", remaining.getString(0))
        assertEquals(42, remaining.getInt(1))
        assertEquals("other", remaining.getJSONObject(2).getString("id"))
        assertFalse(writeWithoutLock)
    }

    private fun command() = ExternalApiCommand.InstallProfile(
        callerPackage = "com.game", requestId = null, profileName = "Managed", effect = LedAnimationType.STATIC,
        color = 123, colorRight = 456, intensity = 120, speed = 0.5f, smoothness = 0.5f, sensitivity = 0.5f,
        saturationBoost = 0f, useCustomSampling = false, useSingleColor = false, breatheWhenCharging = false,
        indicateChargingSpeed = false, flashWhenReady = false, batteryLowColor = null, batteryMidColor = null,
        batteryHighColor = null, cpuCoolColor = null, cpuWarmColor = null, cpuHotColor = null, replaceIfExists = true
    )

    private fun memoryPreferences(): SharedPreferences =
        Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0]] ?: args[1]
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0])
                "edit" -> memoryEditor()
                "hashCode" -> System.identityHashCode(values)
                "equals" -> false
                else -> null
            }
        } as SharedPreferences

    private fun memoryEditor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { pending[args!![0] as String] = args[1]; editor }
                "apply", "commit" -> {
                    if (!Thread.holdsLock(prefs)) writeWithoutLock = true
                    values.putAll(pending)
                    method.name == "commit"
                }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}

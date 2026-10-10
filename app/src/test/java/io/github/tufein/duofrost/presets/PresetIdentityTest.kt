package io.github.tufein.duofrost

import android.content.SharedPreferences
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.external.ExternalApiCommand
import io.github.tufein.duofrost.external.ExternalProfileStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PresetIdentityTest {
    @Test fun legacyNormalizationIsIdempotentAndOnlyAddsIdentity() {
        val raw = """[{"name":"Retro","brightness":117,"customImageFileName":"my-art.png","ownerPackage":"plugin:retro","futureConfig":{"flag":true}},42]"""
        val normalized = PresetIdentity.normalizeJson(raw)!!
        val array = JSONArray(normalized)
        val obj = array.getJSONObject(0)
        assertTrue(PresetIdentity.isValid(obj.getString("id")))
        assertEquals("Retro", obj.getString("name"))
        assertEquals(117, obj.getInt("brightness"))
        assertEquals("my-art.png", obj.getString("customImageFileName"))
        assertEquals("plugin:retro", obj.getString("ownerPackage"))
        assertTrue(obj.getJSONObject("futureConfig").getBoolean("flag"))
        assertEquals(42, array.getInt(1))
        assertEquals(normalized, PresetIdentity.normalizeJson(normalized))
        assertNull(PresetIdentity.normalizeJson("invalid JSON"))
    }

    @Test fun invalidAndDuplicateIdsBecomeDistinctWithoutChangingFirstValidId() {
        val raw = """[{"id":"stable-a","name":"A"},{"id":"stable-a","name":"B"},{"id":"bad id"},{"id":null}]"""
        val array = JSONArray(PresetIdentity.normalizeJson(raw)!!)
        val ids = (0 until array.length()).map { array.getJSONObject(it).getString("id") }
        assertEquals("stable-a", ids.first())
        assertEquals(4, ids.toSet().size)
        assertTrue(ids.all(PresetIdentity::isValid))
    }

    @Test fun repositoryPersistsLegacyIdentityBeforeExposingItAndCachesReads() {
        val values = mutableMapOf<String, Any?>("presets_json" to """[{"name":"Retro"}]""")
        val prefs = memoryPreferences(values)
        val repository = PresetRepository(prefs)
        assertTrue(repository.ensureIds())
        val presets = repository.list()
        assertEquals(presets.single().id, JSONArray(values["presets_json"] as String).getJSONObject(0).getString("id"))
        assertFalse(repository.ensureIds())
        assertSame(presets, repository.list())
        assertEquals(presets.single(), PresetRepository(prefs).findById(presets.single().id))
    }

    @Test fun renameKeepsSceneIdentityArtworkOwnershipAndFutureFields() {
        val values = mutableMapOf<String, Any?>("presets_json" to """[{"name":"Before","id":"stable-a","brightness":100,"customImageFileName":"art.png","ownerPackage":"com.plugin","futureConfig":{"flag":true}}]""")
        val repository = PresetRepository(memoryPreferences(values))
        val preset = repository.list().single()
        repository.save(listOf(preset.copy(name = "After", brightness = 150)))
        val renamed = repository.findById("stable-a")!!
        assertEquals("After", renamed.name)
        assertEquals("stable-a", renamed.id)
        assertEquals("art.png", renamed.customImageFileName)
        assertEquals("com.plugin", renamed.ownerPackage)
        assertTrue(JSONArray(values["presets_json"] as String).getJSONObject(0).getJSONObject("futureConfig").getBoolean("flag"))
    }

    @Test fun appendImportRemapsExistingAndWithinBundleCollisions() {
        val preset = PresetCodec.decode(JSONObject("""{"id":"shared-id","name":"Imported"}"""))
        val imported = PresetIdentity.normalizeImported(listOf(preset, preset.copy(name = "Copy")), setOf("shared-id"))
        assertEquals(2, imported.map { it.id }.toSet().size)
        assertTrue(imported.none { it.id == "shared-id" })
        assertEquals("Imported", imported.first().name)
        assertEquals("shared-id", PresetIdentity.normalizeImported(listOf(preset)).single().id)
        assertTrue(PresetIdentity.isValid(PresetIdentity.normalizeImported(listOf(preset.copy(id = ""))).single().id))
    }

    @Test fun externalNameBasedUpdateRetainsIdOwnerArtworkAndUnknownFields() {
        val values = mutableMapOf<String, Any?>("presets_json" to """[{"id":"managed-id","name":"Managed","ownerPackage":"com.game","customImageFileName":"art.png","futureConfig":true}]""")
        val prefs = memoryPreferences(values)
        val command = ExternalApiCommand.InstallProfile(
            callerPackage = "com.game", requestId = null, profileName = "Managed",
            effect = LedAnimationType.STATIC, color = 123, colorRight = 456,
            intensity = 120, speed = 0.5f, smoothness = 0.5f, sensitivity = 0.5f,
            saturationBoost = 0f, useCustomSampling = false, useSingleColor = false,
            breatheWhenCharging = false, indicateChargingSpeed = false, flashWhenReady = false,
            batteryLowColor = null, batteryMidColor = null, batteryHighColor = null,
            cpuCoolColor = null, cpuWarmColor = null, cpuHotColor = null, replaceIfExists = true
        )
        assertTrue(ExternalProfileStore.installManagedPreset(prefs, command))
        val stored = JSONArray(values["presets_json"] as String).getJSONObject(0)
        assertEquals("managed-id", stored.getString("id"))
        assertEquals("com.game", stored.getString("ownerPackage"))
        assertEquals("art.png", stored.getString("customImageFileName"))
        assertTrue(stored.getBoolean("futureConfig"))
        assertEquals(123, stored.getInt("color"))
        assertFalse(ExternalProfileStore.installManagedPreset(prefs, command.copy(replaceIfExists = false)))
        assertTrue(ExternalProfileStore.installManagedPreset(prefs, command.copy(profileName = "Second")))
        assertNotEquals("managed-id", PresetRepository(prefs).list().last().id)
    }

    @Test fun malformedStoredDataIsNeverRewrittenByMigrationOrSave() {
        val values = mutableMapOf<String, Any?>("presets_json" to "not JSON")
        val repository = PresetRepository(memoryPreferences(values))
        assertFalse(repository.ensureIds())
        assertTrue(repository.list().isEmpty())
        repository.save(emptyList())
        assertEquals("not JSON", values["presets_json"])
    }

    @Test fun wrongTypedPresetPreferencesNeverCrashMigrationReadOrOverwriteOnSave() {
        val replacement = PresetCodec.decode(JSONObject("""{"id":"new","name":"Never written"}"""))
        for (wrongType in listOf<Any>(42, 42L, true, 0.5f, setOf("unexpected"))) {
            val values = mutableMapOf<String, Any?>("presets_json" to wrongType)
            val repository = PresetRepository(memoryPreferences(values))
            assertFalse(repository.ensureIds())
            assertTrue(repository.list().isEmpty())
            assertNull(repository.findById("new"))
            repository.save(listOf(replacement))
            repository.save(emptyList(), preserveUnknownFields = false)
            assertEquals(wrongType, values["presets_json"])
        }
    }

    @Test fun changingValidDataToWrongTypeCannotReturnStaleCachedPresets() {
        val original = """[{"id":"retro","name":"Retro"}]"""
        val values = mutableMapOf<String, Any?>("presets_json" to original)
        val repository = PresetRepository(memoryPreferences(values))
        val cached = repository.list()
        assertEquals("retro", cached.single().id)
        assertSame(cached, repository.list())
        values["presets_json"] = true
        assertFalse(repository.ensureIds())
        assertTrue(repository.list().isEmpty())
        assertNull(repository.findById("retro"))
        assertEquals(true, values["presets_json"])
        values.remove("presets_json")
        assertTrue(repository.list().isEmpty())
        values["presets_json"] = original
        assertEquals("retro", repository.list().single().id)
    }

    @Test fun codecClampsOutOfRangeAndNonFiniteSettingsWithoutChangingValidValues() {
        val invalid = PresetCodec.decode(JSONObject("""{"brightness":999,"speed":-2,"smoothness":2,"sensitivity":"NaN","saturationBoost":"Infinity"}"""))
        assertEquals(255, invalid.brightness)
        assertEquals(0f, invalid.speed)
        assertEquals(1f, invalid.smoothness)
        assertEquals(0.5f, invalid.sensitivity)
        assertEquals(0f, invalid.saturationBoost)
        val valid = PresetCodec.decode(JSONObject("""{"brightness":115,"speed":0.23,"smoothness":0.45,"sensitivity":0.67,"saturationBoost":0.89}"""))
        assertEquals(115, valid.brightness)
        assertEquals(0.23f, valid.speed)
        assertEquals(0.45f, valid.smoothness)
        assertEquals(0.67f, valid.sensitivity)
        assertEquals(0.89f, valid.saturationBoost)
    }

    private fun memoryPreferences(values: MutableMap<String, Any?>): SharedPreferences =
        Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0]] ?: args[1]
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0])
                "edit" -> memoryEditor(values)
                "hashCode" -> System.identityHashCode(values)
                "equals" -> false
                else -> null
            }
        } as SharedPreferences

    private fun memoryEditor(values: MutableMap<String, Any?>): SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { pending[args!![0] as String] = args[1]; editor }
                "apply", "commit" -> { values.putAll(pending); pending.clear(); method.name == "commit" }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}

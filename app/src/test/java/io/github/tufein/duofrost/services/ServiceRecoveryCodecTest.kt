package io.github.tufein.duofrost.services

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServiceRecoveryCodecTest {
    private fun base() = mapOf<String, Any?>(
        "animationType" to "STATIC", "animationColor" to 0xFFCC33AA.toInt(),
        "animationRightColor" to 0xFF1133EE.toInt(), "brightness" to 137,
        "speed" to 0.25f, "smoothness" to 0.75f,
        "batteryLowColorOverride" to 0xFFFF7700.toInt(),
        "ambientDisplayId" to 2, "batterySaverBrightness" to true
    )

    @Test fun unsavedConfigurationSurvivesJsonWithSeparateStickColors() {
        val snapshot = ServiceRecoveryCodec.snapshot(base())!!
        assertEquals(snapshot, ServiceRecoveryCodec.decode(ServiceRecoveryCodec.encode(snapshot)))
        assertNotEquals(snapshot.values["animationColor"], snapshot.values["animationRightColor"])
        assertEquals(2, snapshot.values["ambientDisplayId"])
    }

    @Test fun capturePermissionAndExternalLeaseNeverEnterRecoveryJson() {
        val input = base() + mapOf(
            "data" to "capture-secret", "resultCode" to -1,
            "projectionTokenId" to "single-use-token",
            "external.callerPackage" to "third.party", "external.phaseSeconds" to 123.5,
            "external.durationMs" to 999L, "action" to "EXTERNAL_DISPLAY"
        )
        val encoded = ServiceRecoveryCodec.encode(ServiceRecoveryCodec.snapshot(input)!!)
        listOf("capture-secret", "single-use-token", "third.party", "external.", "resultCode")
            .forEach { assertFalse(encoded.contains(it)) }
    }

    @Test fun decodingOldOrInjectedParcelFieldsDiscardsThem() {
        val root = JSONObject(ServiceRecoveryCodec.encode(ServiceRecoveryCodec.snapshot(base())!!))
        root.getJSONObject("configuration").put("data", "parcel").put("projectionTokenId", "token")
        val restored = ServiceRecoveryCodec.decode(root.toString())!!
        assertFalse(restored.values.containsKey("data"))
        assertFalse(restored.values.containsKey("projectionTokenId"))
    }

    @Test fun partialBrightnessUpdateKeepsAnimationAndBothColors() {
        val before = ServiceRecoveryCodec.snapshot(base())!!
        val after = ServiceRecoveryCodec.merge(before, mapOf("brightness" to 42))!!
        assertEquals(42, after.values["brightness"])
        listOf("animationType", "animationColor", "animationRightColor", "ambientDisplayId")
            .forEach { assertEquals(before.values[it], after.values[it]) }
        assertEquals(137, before.values["brightness"])
    }

    @Test fun restoredGlobalBehaviorSurvivesRecoveryWithoutReplacingTheEffect() {
        val before = ServiceRecoveryCodec.snapshot(base())!!
        val after = ServiceRecoveryCodec.merge(before, mapOf(
            "adaptiveBrightness" to true, "persistentNotification" to false,
            "allowBackgroundRun" to true, "batterySaverBrightness" to false,
            "refreshOutputLimits" to true
        ))!!
        val restored = ServiceRecoveryCodec.decode(ServiceRecoveryCodec.encode(after))!!
        listOf("animationType", "animationColor", "animationRightColor", "brightness",
            "batteryLowColorOverride", "ambientDisplayId").forEach {
            assertEquals("global restore changed $it", before.values[it], restored.values[it])
        }
        assertEquals(true, restored.values["adaptiveBrightness"])
        assertEquals(false, restored.values["persistentNotification"])
        // Background continuation comes from the current preference, never an
        // old base-effect snapshot. One-shot refresh commands are excluded too.
        assertFalse(restored.values.containsKey("allowBackgroundRun"))
        assertEquals(false, restored.values["batterySaverBrightness"])
        assertFalse(restored.values.containsKey("refreshOutputLimits"))
    }

    @Test fun invalidPartialUpdateIsRejectedWithoutChangingOriginal() {
        val before = ServiceRecoveryCodec.snapshot(base())!!
        assertNull(ServiceRecoveryCodec.merge(before, mapOf("brightness" to 256, "speed" to 0.1f)))
        assertEquals(137, before.values["brightness"])
        assertEquals(0.25f, before.values["speed"])
    }

    @Test fun unknownCommandUpdatesCannotReplaceTheBaseEffect() {
        val before = ServiceRecoveryCodec.snapshot(base())!!
        val after = ServiceRecoveryCodec.merge(before, mapOf("external.effect" to "PIPBOY", "data" to Any()))
        assertEquals(before, after)
    }

    @Test fun legacyAnimationNameAndDefaultRightColorRemainCompatible() {
        val restored = ServiceRecoveryCodec.snapshot(mapOf("animationType" to "AMBILIGHT", "animationColor" to -123))!!
        assertEquals("AMBIENT", restored.values["animationType"])
        assertEquals(-123, restored.values["animationRightColor"])
        assertEquals(0xFF00FFFF.toInt(), restored.values["fadeEndRightColor"])
    }

    @Test fun missingOrUnrecognizedEffectDoesNotInventARecoverySession() {
        assertNull(ServiceRecoveryCodec.snapshot(mapOf("brightness" to 100)))
        assertNull(ServiceRecoveryCodec.snapshot(mapOf("animationType" to "UNKNOWN")))
        assertNull(ServiceRecoveryCodec.snapshot(base() + ("performanceProfile" to "UNKNOWN")))
    }

    @Test fun invalidNumericValuesAreRejected() {
        val invalid = listOf(
            "brightness" to -1, "brightness" to 42.5, "brightness" to "42",
            "speed" to Float.NaN, "smoothness" to Double.POSITIVE_INFINITY,
            "sensitivity" to 1.01, "ambientDisplayId" to -1,
            "lowBatteryAlertThreshold" to 0, "animationColor" to Long.MAX_VALUE
        )
        invalid.forEach { (key, value) -> assertNull("$key=$value", ServiceRecoveryCodec.snapshot(base() + (key to value))) }
    }

    @Test fun booleansAndNullsHaveStrictTypes() {
        assertNull(ServiceRecoveryCodec.snapshot(base() + ("batterySaverBrightness" to "true")))
        assertNull(ServiceRecoveryCodec.snapshot(base() + ("animationColor" to null)))
    }

    @Test fun corruptOrFutureSchemaDoesNotCrashOrRestore() {
        listOf(null, "", "{", "[]", "x".repeat(16_385)).forEach {
            assertNull(ServiceRecoveryCodec.decode(it))
        }
        val root = JSONObject(ServiceRecoveryCodec.encode(ServiceRecoveryCodec.snapshot(base())!!))
        root.put("version", 2)
        assertNull(ServiceRecoveryCodec.decode(root.toString()))
        root.put("version", 1).put("schema", "other")
        assertNull(ServiceRecoveryCodec.decode(root.toString()))
    }

    @Test fun snapshotsOwnTheirDataAndKeepPaletteClearSentinel() {
        val input = base().toMutableMap()
        input["cpuHotColorOverride"] = Int.MIN_VALUE
        val stored = ServiceRecoveryCodec.snapshot(input)!!
        input["brightness"] = 0
        assertEquals(137, stored.values["brightness"])
        val restored = ServiceRecoveryCodec.decode(ServiceRecoveryCodec.encode(stored))!!
        assertEquals(Int.MIN_VALUE, restored.values["cpuHotColorOverride"])
    }
}

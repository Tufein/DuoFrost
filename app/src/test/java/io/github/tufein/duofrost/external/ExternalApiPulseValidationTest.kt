package io.github.tufein.duofrost.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalApiPulseValidationTest {

    @Test fun acceptsOnlyDocumentedPulseKinds() {
        assertEquals(ExternalApi.PULSE_KIND_PULSE, ExternalApiDispatcher.validatedPulseKind(null))
        assertEquals(ExternalApi.PULSE_KIND_PULSE, ExternalApiDispatcher.validatedPulseKind("PULSE"))
        assertEquals(ExternalApi.PULSE_KIND_STATIC, ExternalApiDispatcher.validatedPulseKind("STATIC"))

        for (invalid in listOf("", "pulse", "static", "BLINK", "PULSE ")) {
            assertNull("'$invalid' must be rejected", ExternalApiDispatcher.validatedPulseKind(invalid))
        }
    }

    @Test fun serviceContractUsesDistinctMessageKindsAndNegativeFailures() {
        assertEquals(1, ExternalApi.MSG_COMMAND)
        assertEquals(2, ExternalApi.MSG_RESULT)
        assertTrue(ExternalApi.RESULT_REJECTED_UNAVAILABLE < 0)
        assertTrue(ExternalApi.RESULT_REJECTED_CONFLICT < 0)
        assertEquals(
            "io.github.tufein.duofrost.permission.CONTROL_LEDS",
            ExternalApi.PERMISSION
        )
    }
}

package io.github.tufein.duofrost.external

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins DuoFrost's independent IPC identity; upstream-only integrations need adaptation. */
class ExternalApiContractTest {

    @Test
    fun action_pulse_contract_is_stable() {
        assertEquals("io.github.tufein.duofrost.api.ACTION_PULSE", ExternalApi.ACTION_PULSE)
        assertEquals("pulseKind", ExternalApi.EXTRA_PULSE_KIND)
        assertEquals("PULSE", ExternalApi.PULSE_KIND_PULSE)
        assertEquals("STATIC", ExternalApi.PULSE_KIND_STATIC)
    }

    @Test
    fun display_contract_is_stable() {
        assertEquals("io.github.tufein.duofrost.api.ACTION_DISPLAY", ExternalApi.ACTION_DISPLAY)
        assertEquals("phaseSeconds", ExternalApi.EXTRA_PHASE_SECONDS)
        assertEquals("io.github.tufein.duofrost.permission.CONTROL_LEDS", ExternalApi.PERMISSION)
    }
}

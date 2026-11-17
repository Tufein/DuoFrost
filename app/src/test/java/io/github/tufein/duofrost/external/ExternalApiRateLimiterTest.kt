package io.github.tufein.duofrost.external

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalApiRateLimiterTest {

    @Test fun burstIsBoundedAndRefillsWithMonotonicTime() {
        var now = 1_000L
        val limiter = ExternalApiRateLimiter(burst = 3f, sustainedPerSecond = 2f) { now }

        assertTrue(limiter.allow(1))
        assertTrue(limiter.allow(1))
        assertTrue(limiter.allow(1))
        assertFalse(limiter.allow(1))

        now += 500L // 0.5 s at 2/s refills exactly one token
        assertTrue(limiter.allow(1))
        assertFalse(limiter.allow(1))

        now += 100_000L // refill is capped at the burst size
        assertTrue(limiter.allow(1))
        assertTrue(limiter.allow(1))
        assertTrue(limiter.allow(1))
        assertFalse(limiter.allow(1))
    }

    @Test fun callersUseIndependentBucketsAndABackwardClockDoesNotForgeTokens() {
        var now = 5_000L
        val limiter = ExternalApiRateLimiter(burst = 1f, sustainedPerSecond = 1f) { now }

        assertTrue(limiter.allow(7))
        assertFalse(limiter.allow(7))
        assertTrue(limiter.allow(8))

        now -= 10_000L
        assertFalse(limiter.allow(7))

        now += 1_000L
        assertTrue(limiter.allow(7))
    }
}

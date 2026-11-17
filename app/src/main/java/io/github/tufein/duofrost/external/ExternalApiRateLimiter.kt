package io.github.tufein.duofrost.external

import android.os.SystemClock

/**
 * Per-caller token bucket. Uses a monotonic clock: a wall-clock change must not
 * refill the bucket or lock a caller out.
 */
internal class ExternalApiRateLimiter(
    private val burst: Float,
    private val sustainedPerSecond: Float,
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private data class Bucket(var tokens: Float, var lastRefillMs: Long)

    private val buckets = HashMap<Int, Bucket>()

    @Synchronized
    fun allow(key: Int): Boolean {
        val now = nowMillis()
        val bucket = buckets.getOrPut(key) { Bucket(burst, now) }
        val elapsedSec = (now - bucket.lastRefillMs).coerceAtLeast(0L) / 1000f
        bucket.tokens = (bucket.tokens + elapsedSec * sustainedPerSecond).coerceAtMost(burst)
        bucket.lastRefillMs = now
        return if (bucket.tokens >= 1f) {
            bucket.tokens -= 1f
            true
        } else {
            false
        }
    }
}

internal object ExternalApiRateLimiters {
    /** Display/Clear/profile commands: 8 burst, 4/sec sustained. */
    val commands = ExternalApiRateLimiter(burst = 8f, sustainedPerSecond = 4f)

    /** PULSE stays generous for menu navigation but is still bounded per caller. */
    val pulses = ExternalApiRateLimiter(burst = 30f, sustainedPerSecond = 15f)
}

package io.github.tufein.duofrost.scenes

/**
 * Filters brief foreground hops for Smart Scenes, without delaying other
 * conditions or changing the legacy app-profile policy. Only service ticks
 * call this; it never schedules work or starts lighting.
 */
internal class SceneForegroundTracker {
    private var stablePackage: String? = null
    private var candidatePackage: String? = null
    private var candidateSince = 0L
    private var lastObservedAt = 0L
    private var lastTickAt = -1L

    fun resolve(observedPackage: String?, nowElapsed: Long, enabled: Boolean): String? {
        if (!enabled || nowElapsed < 0) {
            clear()
            return null
        }
        // An elapsed-time reset must never leave the old package held forever.
        if (lastTickAt > nowElapsed) clear()
        lastTickAt = nowElapsed
        val observed = observedPackage?.takeIf { it.isNotBlank() }
        if (observed == null) {
            candidatePackage = null
            if (nowElapsed - lastObservedAt >= UNKNOWN_GRACE_MS) stablePackage = null
            return stablePackage
        }
        lastObservedAt = nowElapsed
        if (stablePackage == null || observed == stablePackage) {
            stablePackage = observed
            candidatePackage = null
        } else if (candidatePackage != observed) {
            candidatePackage = observed
            candidateSince = nowElapsed
        } else if (nowElapsed - candidateSince >= SETTLE_MS) {
            stablePackage = observed
            candidatePackage = null
        }
        return stablePackage
    }

    fun clear() {
        stablePackage = null
        candidatePackage = null
        candidateSince = 0L
        lastObservedAt = 0L
        lastTickAt = -1L
    }

    companion object {
        private const val SETTLE_MS = 500L
        private const val UNKNOWN_GRACE_MS = 1_500L
    }
}

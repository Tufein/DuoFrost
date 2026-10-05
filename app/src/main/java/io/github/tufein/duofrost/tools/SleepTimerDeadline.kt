package io.github.tufein.duofrost.tools

/** Monotonic, session-only timer; changing the wall clock cannot extend it. */
data class SleepTimerDeadline(val started: Long, val deadline: Long, val bootCount: Int) {
    fun remaining(now: Long, currentBootCount: Int): Long {
        if (started < 0 || deadline <= started || deadline - started > MAX_DURATION_MS) return 0
        if (bootCount >= 0 && currentBootCount >= 0 && bootCount != currentBootCount) return 0
        if (now < started || now >= deadline) return 0
        return deadline - now
    }

    companion object {
        const val MAX_MINUTES = 120
        const val MAX_DURATION_MS = MAX_MINUTES * 60_000L
        fun create(minutes: Int, now: Long, bootCount: Int): SleepTimerDeadline? {
            if (minutes !in 1..MAX_MINUTES || now < 0) return null
            val duration = minutes * 60_000L
            if (now > Long.MAX_VALUE - duration) return null
            return SleepTimerDeadline(now, now + duration, bootCount)
        }
    }
}

package io.github.tufein.duofrost.tools

enum class PerformanceProfile(val intervalMs: Long) {
    LOW(500L),
    MEDIUM(100L),
    HIGH(33L),
    RAGNAROK(0L)
}
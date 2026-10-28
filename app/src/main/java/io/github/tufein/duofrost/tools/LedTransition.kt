package io.github.tufein.duofrost.tools

/** Bounded raw-color interpolation. Output ceilings and mute are applied afterwards. */
class LedTransition {
    private var origin: IntArray? = null
    private var duration = 0L
    private var start = -1L
    fun begin(colors: IntArray, durationMs: Int) {
        duration = durationMs.coerceIn(0, 2000).toLong()
        origin = colors.copyOf().takeIf { duration > 0 }
        start = -1
    }
    fun frameArrived(now: Long) { if (origin != null && start < 0) start = now }
    fun active(now: Long): Boolean = origin != null && (start < 0 || now - start < duration)
    fun color(zone: Int, target: Int, now: Long): Int {
        val from = origin?.getOrNull(zone) ?: return target
        val fraction = if (start < 0) 0f else ((now - start).toFloat() / duration).coerceIn(0f, 1f)
        var result = 0
        for (shift in listOf(16, 8, 0)) {
            val a = (from ushr shift) and 255
            val b = (target ushr shift) and 255
            result = result or ((a + (b - a) * fraction).toInt().coerceIn(0, 255) shl shift)
        }
        return result
    }
    fun cancel() { origin = null; start = -1 }
}

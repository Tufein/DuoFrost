package io.github.tufein.duofrost.tools

/** Reads the raw dump written by `screencap <file>`: a small header, then RGBA pixels. */
internal object RawScreenFrame {
    data class Summary(
        val width: Int,
        val height: Int,
        val format: Int,
        val headerBytes: Int,
        val average: Int,
        val left: Int,
        val right: Int
    )

    private const val FORMAT_RGBA_8888 = 1
    private const val FORMAT_RGBX_8888 = 2
    private const val FORMAT_BGRA_8888 = 5
    private const val GRID_COLUMNS = 32
    private const val GRID_ROWS = 18

    /** Returns null when the bytes are not a complete 8-bit, four-channel frame. */
    fun summarize(bytes: ByteArray): Summary? {
        if (bytes.size < 12) return null
        val width = int(bytes, 0)
        val height = int(bytes, 4)
        val format = int(bytes, 8)
        if (width !in 1..16384 || height !in 1..16384) return null
        val pixelBytes = width.toLong() * height * 4
        val header = bytes.size - pixelBytes
        // Android 13 writes four header ints, older releases three.
        if (header != 12L && header != 16L) return null
        val order = when (format) {
            FORMAT_RGBA_8888, FORMAT_RGBX_8888 -> intArrayOf(0, 1, 2)
            FORMAT_BGRA_8888 -> intArrayOf(2, 1, 0)
            else -> return null
        }
        val sums = Array(2) { LongArray(3) }
        val counts = IntArray(2)
        for (row in 0 until GRID_ROWS) {
            val y = ((row + 0.5) * height / GRID_ROWS).toInt().coerceIn(0, height - 1)
            for (column in 0 until GRID_COLUMNS) {
                val x = ((column + 0.5) * width / GRID_COLUMNS).toInt().coerceIn(0, width - 1)
                val offset = header.toInt() + (y.toLong() * width + x).toInt() * 4
                val half = if (column < GRID_COLUMNS / 2) 0 else 1
                for (channel in 0..2) sums[half][channel] += (bytes[offset + order[channel]].toInt() and 255).toLong()
                counts[half]++
            }
        }
        fun average(): Int {
            val count = (counts[0] + counts[1]).coerceAtLeast(1)
            val r = ((sums[0][0] + sums[1][0]) / count).toInt()
            val g = ((sums[0][1] + sums[1][1]) / count).toInt()
            val b = ((sums[0][2] + sums[1][2]) / count).toInt()
            return (r shl 16) or (g shl 8) or b
        }
        fun single(half: Int): Int {
            val c = counts[half].coerceAtLeast(1)
            return ((sums[half][0] / c).toInt() shl 16) or ((sums[half][1] / c).toInt() shl 8) or (sums[half][2] / c).toInt()
        }
        return Summary(width, height, format, header.toInt(), average(), single(0), single(1))
    }

    private fun int(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8) or
            ((bytes[at + 2].toInt() and 255) shl 16) or ((bytes[at + 3].toInt() and 255) shl 24)
}

package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawScreenFrameTest {
    private fun frame(width: Int, height: Int, format: Int, header: Int, pixel: (Int, Int) -> Int): ByteArray {
        val bytes = ByteArray(header + width * height * 4)
        fun put(at: Int, value: Int) { for (i in 0..3) bytes[at + i] = (value ushr (8 * i)).toByte() }
        put(0, width); put(4, height); put(8, format)
        for (y in 0 until height) for (x in 0 until width) {
            val rgb = pixel(x, y)
            val at = header + (y * width + x) * 4
            bytes[at] = (rgb ushr 16).toByte(); bytes[at + 1] = (rgb ushr 8).toByte()
            bytes[at + 2] = rgb.toByte(); bytes[at + 3] = -1
        }
        return bytes
    }

    @Test fun readsTheSixteenByteHeaderAndSplitsLeftFromRight() {
        val summary = RawScreenFrame.summarize(frame(64, 36, 1, 16) { x, _ -> if (x < 32) 0xff0000 else 0x0000ff })!!
        assertEquals(16, summary.headerBytes)
        assertEquals(0xff0000, summary.left)
        assertEquals(0x0000ff, summary.right)
        assertEquals(0x7f007f, summary.average)
    }

    @Test fun acceptsTheTwelveByteHeader() {
        assertEquals(12, RawScreenFrame.summarize(frame(32, 18, 1, 12) { _, _ -> 0x102030 })!!.headerBytes)
    }

    @Test fun swapsChannelsForBgraFrames() {
        assertEquals(0x0000ff, RawScreenFrame.summarize(frame(32, 18, 5, 16) { _, _ -> 0xff0000 })!!.average)
    }

    @Test fun rejectsTruncatedOrUnknownFrames() {
        val whole = frame(32, 18, 1, 16) { _, _ -> 0 }
        assertNull(RawScreenFrame.summarize(whole.copyOf(whole.size - 100)))
        assertNull(RawScreenFrame.summarize(frame(32, 18, 99, 16) { _, _ -> 0 }))
        assertNull(RawScreenFrame.summarize(ByteArray(4)))
    }
}

package io.github.tufein.duofrost.tools

import kotlin.math.abs

/** One capture owns its recorder and buffer until both the reader and cleanup finish. */
internal class AudioCaptureSession(
    private val source: Source,
    bufferSamples: Int,
    private val skipInterval: Int,
    private val onIntensity: (Float) -> Unit,
    private val onFailure: (Exception) -> Unit,
    private val onFinished: (AudioCaptureSession) -> Unit = {}
) {
    interface Source {
        fun start()
        fun read(buffer: ShortArray): Int
        fun stop()
        fun release()
    }

    private val lock = Any()
    private val samples = ShortArray(bufferSamples.coerceAtLeast(1))
    @Volatile private var running = false
    private var closed = false
    private var thread: Thread? = null

    fun start() {
        synchronized(lock) {
            if (closed || thread != null) return
            running = true
            thread = Thread({ capture() }, "AudioCapture").also { it.start() }
        }
    }

    fun stop(timeoutMs: Long = 200L) {
        val reader = synchronized(lock) {
            running = false
            // AudioRecord.read is blocking: stop/release it before waiting for the reader.
            close()
            thread
        }
        if (reader != null && reader !== Thread.currentThread()) {
            reader.interrupt()
            runCatching { reader.join(timeoutMs.coerceAtLeast(1L)) }
            if (reader.isAlive) reportFailure(IllegalStateException("Audio capture reader did not stop within timeout"))
        }
    }

    private fun capture() {
        try {
            synchronized(lock) {
                if (!running || closed) return
                source.start()
            }
            var skip = 0
            while (running) {
                val read = source.read(samples)
                if (!running) break
                // A blocking read with a nonempty buffer cannot make progress on an error.
                // Retrying here spins at urgent-audio priority after a recorder/server failure.
                if (read <= 0) throw IllegalStateException("Audio capture read failed: $read")
                if (skip > 0) {
                    skip--
                    continue
                }
                skip = skipInterval
                var peak = 0
                for (index in 0 until minOf(read, samples.size)) {
                    peak = maxOf(peak, abs(samples[index].toInt()))
                }
                val intensity = (peak.toFloat() / Short.MAX_VALUE * 5f).coerceIn(0f, 1f)
                synchronized(lock) {
                    if (running && !closed) onIntensity(intensity)
                }
            }
        } catch (error: Exception) {
            if (running) reportFailure(error)
        } finally {
            synchronized(lock) {
                val shouldSilence = running
                running = false
                if (shouldSilence) {
                    try {
                        onIntensity(0f)
                    } catch (error: Exception) {
                        reportFailure(error)
                    }
                }
                close()
            }
            onFinished(this)
        }
    }

    private fun close() {
        // Caller holds lock. Teardown may come from Stop, a route event or the reader.
        if (closed) return
        closed = true
        try {
            source.stop()
        } catch (error: Exception) {
            reportFailure(error)
        } finally {
            try {
                source.release()
            } catch (error: Exception) {
                reportFailure(error)
            }
        }
    }

    private fun reportFailure(error: Exception) {
        runCatching { onFailure(error) }
    }
}

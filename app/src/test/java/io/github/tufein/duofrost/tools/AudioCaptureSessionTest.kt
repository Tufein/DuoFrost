package io.github.tufein.duofrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AudioCaptureSessionTest {
    @Test fun failedReadsDoNotSpinAndAlwaysReleaseTheRecorder() {
        for (result in listOf(-6, -3, -2, -1, 0)) {
            val reads = AtomicInteger()
            val source = FakeSource { reads.incrementAndGet(); result }
            val intensities = Collections.synchronizedList(mutableListOf<Float>())
            val finished = CountDownLatch(1)
            val session = session(source, intensities, finished)
            session.start()
            assertTrue("Reader did not terminate for result $result", finished.await(1, TimeUnit.SECONDS))
            session.stop()
            assertEquals(1, reads.get())
            assertEquals(listOf(0f), intensities)
            assertEquals(1, source.stops.get())
            assertEquals(1, source.releases.get())
        }
    }

    @Test fun recorderFailureClearsTheLastBrightIntensity() {
        var reads = 0
        val source = FakeSource { buffer ->
            if (reads++ == 0) {
                buffer[0] = 4096
                1
            } else -6
        }
        val intensities = Collections.synchronizedList(mutableListOf<Float>())
        val finished = CountDownLatch(1)
        session(source, intensities, finished).start()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        assertEquals(2, intensities.size)
        assertEquals(4096f / Short.MAX_VALUE * 5f, intensities[0], 0.00001f)
        assertEquals(0f, intensities[1], 0f)
    }

    @Test fun stopUnblocksAReadBeforeJoiningAndDropsItsLateSample() {
        val reading = CountDownLatch(1)
        val unblocked = CountDownLatch(1)
        val source = FakeSource(
            read = { buffer ->
                reading.countDown()
                assertTrue(unblocked.await(1, TimeUnit.SECONDS))
                buffer[0] = Short.MAX_VALUE
                1
            },
            onStop = { unblocked.countDown() }
        )
        val intensities = Collections.synchronizedList(mutableListOf<Float>())
        val finished = CountDownLatch(1)
        val session = session(source, intensities, finished)
        session.start()
        assertTrue(reading.await(1, TimeUnit.SECONDS))
        session.stop()
        assertEquals("Stop should wait only after unblocking the read", 0L, finished.count)
        assertTrue(intensities.isEmpty())
        assertEquals(1, source.stops.get())
        assertEquals(1, source.releases.get())
    }

    @Test fun releaseStillRunsWhenStoppingTheRecorderThrows() {
        val source = FakeSource(read = { -6 }, onStop = { throw IllegalStateException("Recorder died") })
        val finished = CountDownLatch(1)
        val session = session(source, mutableListOf(), finished)
        session.start()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        session.stop()
        assertEquals(1, source.stops.get())
        assertEquals(1, source.releases.get())
    }

    @Test fun failedStartAlsoSilencesAndReleasesTheRecorder() {
        val source = FakeSource(read = { error("Read must not start") }, onStart = { error("Start failed") })
        val intensities = Collections.synchronizedList(mutableListOf<Float>())
        val finished = CountDownLatch(1)
        session(source, intensities, finished).start()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(0f), intensities)
        assertEquals(1, source.releases.get())
    }

    @Test fun aReaderFinishingAfterItsStopCannotAffectTheReplacement() {
        val oldReading = CountDownLatch(1)
        val finishOldRead = CountDownLatch(1)
        val oldSource = FakeSource { buffer ->
            oldReading.countDown()
            while (finishOldRead.count != 0L) {
                try {
                    finishOldRead.await()
                } catch (_: InterruptedException) {
                    // A stuck native read may outlive both stop and interrupt.
                }
            }
            buffer[0] = Short.MAX_VALUE
            1
        }
        val oldIntensities = Collections.synchronizedList(mutableListOf<Float>())
        val oldFinished = CountDownLatch(1)
        val oldSession = session(oldSource, oldIntensities, oldFinished)
        oldSession.start()
        assertTrue(oldReading.await(1, TimeUnit.SECONDS))
        oldSession.stop(timeoutMs = 1)
        try {
            var reads = 0
            val newSource = FakeSource { buffer ->
                if (reads++ == 0) {
                    buffer[0] = 1024
                    1
                } else -6
            }
            val newIntensities = Collections.synchronizedList(mutableListOf<Float>())
            val newFinished = CountDownLatch(1)
            session(newSource, newIntensities, newFinished).start()
            assertTrue(newFinished.await(1, TimeUnit.SECONDS))
            finishOldRead.countDown()
            assertTrue(oldFinished.await(1, TimeUnit.SECONDS))
            assertTrue(oldIntensities.isEmpty())
            assertEquals(1024f / Short.MAX_VALUE * 5f, newIntensities[0], 0.00001f)
            assertEquals(listOf(1024f / Short.MAX_VALUE * 5f, 0f), newIntensities)
            assertEquals(1, oldSource.releases.get())
            assertEquals(1, newSource.releases.get())
        } finally {
            finishOldRead.countDown()
            oldSession.stop()
        }
    }

    @Test fun failingConsumerCannotPreventRecorderCleanup() {
        val source = FakeSource { buffer -> buffer[0] = 4096; 1 }
        val finished = CountDownLatch(1)
        AudioCaptureSession(source, 16, 0,
            onIntensity = { error("Consumer failed") }, onFailure = {},
            onFinished = { finished.countDown() }
        ).start()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        assertEquals(1, source.releases.get())
    }

    private fun session(
        source: FakeSource,
        intensities: MutableList<Float>,
        finished: CountDownLatch
    ) = AudioCaptureSession(source, 16, 0,
        onIntensity = { intensities.add(it) }, onFailure = {},
        onFinished = { finished.countDown() }
    )

    private class FakeSource(
        val onStop: () -> Unit = {},
        val onStart: () -> Unit = {},
        val read: (ShortArray) -> Int
    ) : AudioCaptureSession.Source {
        val stops = AtomicInteger()
        val releases = AtomicInteger()
        override fun start() = onStart()
        override fun read(buffer: ShortArray) = read.invoke(buffer)
        override fun stop() { stops.incrementAndGet(); onStop() }
        override fun release() { releases.incrementAndGet() }
    }
}

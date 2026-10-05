package io.github.tufein.duofrost.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
class AudioAnalyzer(
    private val mediaProjection: MediaProjection,
    private val performanceProfile: PerformanceProfile,
    private val callback: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE_HZ = 8000
        private const val DEFAULT_BUFFER_BYTES = 512
    }

    private val sessionLock = Any()
    private var activeSession: AudioCaptureSession? = null

    fun start() {
        synchronized(sessionLock) {
            if (activeSession != null) return
            var record: AudioRecord? = null
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, channelConfig, encoding)
                check(minBufferSize > 0) { "Unsupported audio capture format: $minBufferSize" }
                val bufferBytes = maxOf(DEFAULT_BUFFER_BYTES, minBufferSize)
                val recorder = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                record = recorder
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }

                val skipInterval = when {
                    performanceProfile.intervalMs >= 32L -> 3
                    performanceProfile.intervalMs >= 16L -> 1
                    else -> 0
                }
                lateinit var session: AudioCaptureSession
                val routeListener = AudioRouting.OnRoutingChangedListener { route ->
                    val current = synchronized(sessionLock) { activeSession === session }
                    if (!current) return@OnRoutingChangedListener
                    val routedRecord = route as? AudioRecord ?: return@OnRoutingChangedListener
                    // A queued route event can race recorder release, even after listener removal.
                    val blocked = runCatching {
                        HardwareDeviceBlacklist.isBlockedMicrophoneDevice(routedRecord.routedDevice)
                    }.getOrDefault(false)
                    if (blocked) {
                        Log.w(TAG, "Blocked physical microphone route detected; stopping capture")
                        stopSession(session)
                    }
                }
                val source = object : AudioCaptureSession.Source {
                    override fun start() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                        recorder.addOnRoutingChangedListener(routeListener, null)
                        recorder.startRecording()
                        check(!HardwareDeviceBlacklist.isBlockedMicrophoneDevice(recorder.routedDevice)) {
                            "Initial audio route resolved to a blocked microphone"
                        }
                    }

                    override fun read(buffer: ShortArray): Int = recorder.read(buffer, 0, buffer.size)

                    override fun stop() {
                        runCatching { recorder.removeOnRoutingChangedListener(routeListener) }
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                    }

                    override fun release() = recorder.release()
                }
                session = AudioCaptureSession(
                    source, (bufferBytes / 2).coerceAtLeast(128), skipInterval,
                    onIntensity = { intensity ->
                        synchronized(sessionLock) {
                            if (activeSession === session) callback(intensity)
                        }
                    },
                    onFailure = { error -> Log.w(TAG, "Audio capture failed", error) },
                    onFinished = { finished ->
                        synchronized(sessionLock) {
                            if (activeSession === finished) activeSession = null
                        }
                    }
                )
                activeSession = session
                session.start()
            } catch (error: Exception) {
                Log.w(TAG, "Audio analyzer failed to start", error)
                val failedSession = activeSession
                activeSession = null
                if (failedSession != null) failedSession.stop()
                else runCatching { record?.release() }
                silence()
            }
        }
    }

    fun stop() {
        val session = synchronized(sessionLock) {
            activeSession.also { activeSession = null }
        }
        session?.stop()
    }

    private fun stopSession(session: AudioCaptureSession) {
        synchronized(sessionLock) {
            if (activeSession !== session) return
            activeSession = null
            silence()
        }
        session.stop()
    }

    private fun silence() {
        runCatching { callback(0f) }.onFailure { Log.w(TAG, "Audio consumer failed", it) }
    }
}

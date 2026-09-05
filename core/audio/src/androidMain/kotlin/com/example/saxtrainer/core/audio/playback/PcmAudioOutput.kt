package com.example.saxtrainer.core.audio.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.saxtrainer.core.audio.AudioConfig

/** Why a playback stream could not be opened or kept running. */
sealed interface AudioOutputError {
    data object UnsupportedConfiguration : AudioOutputError

    data object InitializationFailed : AudioOutputError
}

/**
 * Thin blocking wrapper around [AudioTrack] for mono 16-bit PCM playback.
 *
 * Blocking [write] calls pace the caller's render loop, and [playbackHeadFrames] exposes the audio
 * clock, which lets callers schedule events against actual playback rather than against wall time.
 * Reusable by any feature that needs to render audio (metronome now, drones or ear training later).
 */
class PcmAudioOutput(
    private val config: AudioConfig = AudioConfig(),
    private val bufferFrames: Int = 0,
) {
    private var track: AudioTrack? = null

    val sampleRate: Int get() = config.sampleRate

    /** Frames the hardware has actually played since [open]. Wraps around like [AudioTrack] does. */
    val playbackHeadFrames: Int get() = track?.playbackHeadPosition ?: 0

    /** Opens the stream. Returns the failure reason instead of throwing so callers can show it. */
    fun open(): AudioOutputError? {
        close()
        val minBytes = AudioTrack.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) return AudioOutputError.UnsupportedConfiguration

        val requestedBytes = if (bufferFrames > 0) bufferFrames * BYTES_PER_FRAME else 0
        val bufferBytes = maxOf(minBytes, requestedBytes)
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: UnsupportedOperationException) {
            return AudioOutputError.UnsupportedConfiguration
        } catch (e: IllegalArgumentException) {
            return AudioOutputError.UnsupportedConfiguration
        } catch (e: IllegalStateException) {
            return AudioOutputError.InitializationFailed
        }

        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            return AudioOutputError.InitializationFailed
        }
        track = newTrack
        return null
    }

    fun play() {
        track?.play()
    }

    /**
     * Blocking write of [count] frames from [buffer]. Returns the frames accepted, or a negative
     * [AudioTrack] error code.
     */
    fun write(buffer: ShortArray, count: Int): Int {
        val current = track ?: return AudioTrack.ERROR_INVALID_OPERATION
        return current.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
    }

    fun close() {
        track?.let { current ->
            runCatching { current.pause() }
            runCatching { current.flush() }
            runCatching { current.stop() }
            current.release()
        }
        track = null
    }

    private companion object {
        const val BYTES_PER_FRAME = 2
    }
}

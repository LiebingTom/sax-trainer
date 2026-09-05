package com.example.saxtrainer.core.audio.capture

import com.example.saxtrainer.core.audio.AudioConfig
import kotlinx.coroutines.flow.Flow

/**
 * Capture parameters. [frameSize] is the analysis window handed to DSP and [hopSize] how far the
 * window advances between frames, so overlapping analysis is a configuration choice rather than a
 * property of any algorithm.
 */
data class AudioCaptureConfig(
    val audio: AudioConfig = AudioConfig(),
    val frameSize: Int = DEFAULT_FRAME_SIZE,
    val hopSize: Int = DEFAULT_HOP_SIZE,
) {
    init {
        require(frameSize > 0) { "frameSize must be positive" }
        require(hopSize in 1..frameSize) { "hopSize must be in 1..frameSize" }
    }

    /**
     * The rate this configuration asks for. What a platform actually gives is on each [AudioFrame],
     * and on the web the two can differ - the browser picks its own and may resample. Anything that
     * has to be right about the rate reads the frame, never this.
     */
    val sampleRate: Int get() = audio.sampleRate

    companion object {
        const val DEFAULT_FRAME_SIZE = 4096
        const val DEFAULT_HOP_SIZE = 2048
    }
}

/**
 * One analysis window of mono audio, normalised to -1.0..1.0.
 *
 * [samples] is owned by the capture implementation and reused for the next frame: consumers must
 * finish with it (or copy it) inside the collector body. This keeps the real-time path free of
 * per-frame allocations.
 *
 * The sample counters carry the audio clock. Deriving durations from them instead of from wall
 * time makes measurements independent of scheduling and processing latency, which matters for this
 * exercise today and for onset and rhythm analysis later.
 */
class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    /** Index of the first sample of this window, counted from the start of capture. */
    var startSample: Long = 0L
        private set

    /**
     * Samples in this window that were not part of the previous one. Summing this over all frames
     * gives the exact amount of audio seen, with no double counting of the overlap.
     */
    var newSamples: Int = 0
        private set

    /** Stamps the audio clock onto the frame. Called by [AudioCapture] implementations only. */
    fun position(startSample: Long, newSamples: Int) {
        this.startSample = startSample
        this.newSamples = newSamples
    }

    /** Index just past the last sample of this window. */
    val endSample: Long get() = startSample + samples.size

    /** Audio time at the end of this window, in seconds since capture started. */
    val endTimeSeconds: Double get() = endSample.toDouble() / sampleRate

    /** Duration this window adds to the captured timeline. */
    val newDurationSeconds: Double get() = newSamples.toDouble() / sampleRate
}

/** Why capture could not start or could not continue. */
sealed interface AudioCaptureError {
    data object PermissionDenied : AudioCaptureError

    data object UnsupportedConfiguration : AudioCaptureError

    data object InitializationFailed : AudioCaptureError

    data object DeviceUnavailable : AudioCaptureError

    data class ReadFailed(val code: Int) : AudioCaptureError
}

/** Either audio or the reason there is none. Failures are values, never exceptions. */
sealed interface AudioCaptureEvent {
    data class Frame(val frame: AudioFrame) : AudioCaptureEvent

    data class Failed(val error: AudioCaptureError) : AudioCaptureEvent
}

/**
 * Microphone input, shared by every feature that listens to the player (long tones now; tuner,
 * pitch trainer and rhythm trainer later). Features must never open their own recorder.
 */
interface AudioCapture {
    /**
     * Cold flow of analysis frames. Recording starts on collection and stops when the collector is
     * cancelled.
     *
     * Collect it on a background dispatcher where the platform has one - the Android implementation
     * blocks on every read. Where it does not, the implementation must not block: the browser has
     * one thread and it is also the one that draws.
     */
    fun frames(config: AudioCaptureConfig = AudioCaptureConfig()): Flow<AudioCaptureEvent>
}

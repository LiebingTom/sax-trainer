package com.example.saxtrainer.core.testing

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plays a steady tone into a trainer, so the real gate, the real YIN and the real note lookup all
 * run - a stubbed pitch would test none of them.
 *
 * [frequencyHz] of null is silence, and [noisy] is a sound with no pitch in it. Keeping the two
 * apart is the whole point: an exercise must never report a breath as a wrong note. Both are
 * settable while a test runs, which is how a test plays one note and then another.
 */
class FakeMicrophone(
    private val frameDurationMs: Long,
    @Volatile var frequencyHz: Double? = null,
    @Volatile var noisy: Boolean = false,
) : AudioCapture {

    override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
        val frame = AudioFrame(FloatArray(config.frameSize), config.sampleRate)
        val noise = Random(NOISE_SEED)
        var samples = 0L
        while (true) {
            delay(frameDurationMs)
            val tone = frequencyHz
            for (i in frame.samples.indices) {
                frame.samples[i] = when {
                    noisy -> (noise.nextDouble() - 0.5).toFloat()
                    tone == null -> 0f
                    else -> (AMPLITUDE * sin(2.0 * PI * tone * i / config.sampleRate)).toFloat()
                }
            }
            samples += config.hopSize
            frame.position(startSample = samples, newSamples = config.hopSize)
            emit(AudioCaptureEvent.Frame(frame))
        }
    }

    private companion object {
        const val NOISE_SEED = 7
        const val AMPLITUDE = 0.5
    }
}

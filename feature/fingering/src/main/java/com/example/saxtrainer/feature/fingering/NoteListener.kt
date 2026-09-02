package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.dsp.level.SignalGate
import com.example.saxtrainer.core.dsp.pitch.YinPitchDetector
import com.example.saxtrainer.core.music.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A note the microphone is hearing, in concert pitch. Null anywhere means "nothing reliable". */
data class DetectedNote(
    val note: Note,
    val frequencyHz: Float,
    val confidence: Float,
)

/**
 * Reports which note is being played, or nothing when the player is silent or the estimate is not
 * trustworthy.
 *
 * This is the small part of the long tone pipeline that a fingering check needs - gate, pitch,
 * confidence - without level, spectrum or cent statistics. It deliberately re-uses the same
 * `core:dsp` primitives rather than sharing `LongToneProcessor`, which measures far more per frame
 * and is scoped to a target note.
 */
class NoteListener(
    private val audioCapture: AudioCapture,
    private val captureConfig: AudioCaptureConfig = AudioCaptureConfig(),
    private val settings: ListeningSettings = ListeningSettings(),
) {
    data class ListeningSettings(
        val gateOpenDbfs: Float = -42f,
        val gateCloseDbfs: Float = -48f,
        val minConfidence: Float = 0.75f,
    )

    /**
     * Emits on every capture frame: the detected note, or null while nothing is playing. Capture
     * failures end the flow rather than throwing, because a fingering exercise must carry on
     * without a microphone.
     */
    fun detectedNotes(): Flow<DetectedNote?> = flow {
        val detector = YinPitchDetector(captureConfig.sampleRate)
        val gate = SignalGate(settings.gateOpenDbfs, settings.gateCloseDbfs)

        audioCapture.frames(captureConfig).collect { event ->
            when (event) {
                is AudioCaptureEvent.Failed -> return@collect

                is AudioCaptureEvent.Frame -> {
                    val samples = event.frame.samples
                    val levelDbfs = RmsCalculator.toDbfs(RmsCalculator.rms(samples))
                    emit(
                        if (!gate.update(levelDbfs)) {
                            null
                        } else {
                            detector.detect(samples)
                                ?.takeIf { it.confidence >= settings.minConfidence }
                                ?.let { estimate ->
                                    DetectedNote(
                                        note = Note.nearestTo(estimate.frequencyHz.toDouble()),
                                        frequencyHz = estimate.frequencyHz,
                                        confidence = estimate.confidence,
                                    )
                                }
                        }
                    )
                }
            }
        }
    }
}

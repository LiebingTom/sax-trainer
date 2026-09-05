package com.example.saxtrainer.core.audio.listening

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.dsp.level.SignalGate
import com.example.saxtrainer.core.dsp.pitch.YinPitchDetector
import com.example.saxtrainer.core.music.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** A note the microphone is hearing, in concert pitch. Null anywhere means "nothing reliable". */
data class DetectedNote(
    val note: Note,
    val frequencyHz: Float,
    val confidence: Float,
)

/**
 * What the microphone is making of one frame.
 *
 * The distinction that matters is between [Silent] and [Unclear]: nobody playing is not the same
 * thing as somebody playing badly, and an exercise that judges what it hears must not report a
 * wobbling attack as a wrong note. [DetectedNote] alone cannot say which of the two happened, which
 * is why this exists beside it rather than replacing it.
 */
sealed interface Hearing {

    /** Below the gate - nobody is playing. */
    data object Silent : Hearing

    /** Loud enough, but the pitch could not be trusted: a squeak, a breath, an unsettled attack. */
    data class Unclear(val confidence: Float) : Hearing

    /** A pitch worth acting on. */
    data class Pitched(val detected: DetectedNote) : Hearing
}

/**
 * Reports which note is being played, and - through [listen] - why it is reporting nothing when it
 * is not.
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
     * Emits on every capture frame: the detected note, or null while nothing reliable is being
     * played. The plain form for callers that only ever act on a pitch.
     */
    fun detectedNotes(): Flow<DetectedNote?> =
        listen().map { (it as? Hearing.Pitched)?.detected }

    /**
     * Emits on every capture frame, saying which of silence, an untrustworthy sound and a real pitch
     * it heard. Capture failures end the flow rather than throwing, because an exercise must carry
     * on without a microphone.
     */
    fun listen(): Flow<Hearing> = flow {
        val gate = SignalGate(settings.gateOpenDbfs, settings.gateCloseDbfs)
        // The rate comes from the frames, not from the configuration that asked for them: a browser
        // hands out whatever its audio context runs at and is free to ignore what was requested, so
        // the configured rate is a preference and the frame's is the fact. Building the detector
        // from anything else would put every reported pitch out by the ratio between the two.
        var detector: YinPitchDetector? = null
        var detectorSampleRate = 0

        audioCapture.frames(captureConfig).collect { event ->
            when (event) {
                is AudioCaptureEvent.Failed -> return@collect

                is AudioCaptureEvent.Frame -> {
                    val samples = event.frame.samples
                    val existing = detector
                    val pitchDetector = if (existing != null &&
                        detectorSampleRate == event.frame.sampleRate
                    ) {
                        existing
                    } else {
                        detectorSampleRate = event.frame.sampleRate
                        YinPitchDetector(detectorSampleRate).also { detector = it }
                    }
                    val levelDbfs = RmsCalculator.toDbfs(RmsCalculator.rms(samples))
                    emit(
                        if (!gate.update(levelDbfs)) {
                            Hearing.Silent
                        } else {
                            val estimate = pitchDetector.detect(samples)
                            when {
                                estimate == null -> Hearing.Unclear(0f)
                                estimate.confidence < settings.minConfidence ->
                                    Hearing.Unclear(estimate.confidence)

                                else -> Hearing.Pitched(
                                    DetectedNote(
                                        note = Note.nearestTo(estimate.frequencyHz.toDouble()),
                                        frequencyHz = estimate.frequencyHz,
                                        confidence = estimate.confidence,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

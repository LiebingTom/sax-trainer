package com.example.saxtrainer.probe

import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.dsp.level.SignalGate
import com.example.saxtrainer.core.dsp.pitch.YinConfig
import com.example.saxtrainer.core.dsp.pitch.YinPitchDetector
import com.example.saxtrainer.core.music.Note

/**
 * M3 PROBE - TEMPORARY. Delete with the probe/ directory.
 *
 * `NoteListener`'s decision, frame by frame, with `NoteListener` itself left where it is.
 *
 * The probe cannot call it: it lives in `:core:audio`, which is still an Android library and which
 * M3 must not touch. So the ten lines of decision are repeated here **against the same
 * `core:dsp` primitives and the same thresholds** - the same `SignalGate`, the same
 * `RmsCalculator`, the same `YinPitchDetector`, the same `Note.nearestTo`. No second pitch detector
 * exists, and none of `core:dsp` was changed for the probe.
 *
 * If the probe says yes, M4 deletes this file and moves `NoteListener` itself into `commonMain`.
 */
class ProbeListener(
    sampleRate: Int,
    yin: YinConfig = YinConfig(),
    gateOpenDbfs: Float = -42f,
    gateCloseDbfs: Float = -48f,
    private val minConfidence: Float = 0.75f,
) {
    /** What the microphone made of one frame. Mirrors `Hearing` without depending on it. */
    sealed interface Heard {
        data object Silent : Heard

        data class Unclear(val confidence: Float) : Heard

        data class Pitched(
            val note: Note,
            val frequencyHz: Float,
            val confidence: Float,
        ) : Heard
    }

    private val detector = YinPitchDetector(sampleRate, yin)
    private val gate = SignalGate(gateOpenDbfs, gateCloseDbfs)

    var lastLevelDbfs: Float = -120f
        private set

    fun hear(samples: FloatArray): Heard {
        val levelDbfs = RmsCalculator.toDbfs(RmsCalculator.rms(samples))
        lastLevelDbfs = levelDbfs
        if (!gate.update(levelDbfs)) return Heard.Silent

        val estimate = detector.detect(samples) ?: return Heard.Unclear(0f)
        if (estimate.confidence < minConfidence) return Heard.Unclear(estimate.confidence)
        return Heard.Pitched(
            note = Note.nearestTo(estimate.frequencyHz.toDouble()),
            frequencyHz = estimate.frequencyHz,
            confidence = estimate.confidence,
        )
    }
}

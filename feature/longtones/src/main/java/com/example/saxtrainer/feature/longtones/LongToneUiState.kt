package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Transposition

/**
 * Where the exercise currently is.
 *
 * [Running] ends when the attempt has lasted the target duration in audio time, whether or not a
 * tone was actually heard: for this version the target duration is the length of the *attempt*, not
 * an amount of validated playing. `LongToneProcessor.soundingSeconds` already accumulates the
 * gated-on portion, so a later version can complete on validated held time instead by changing the
 * completion condition in [LongToneViewModel] - no change to this state model is needed.
 */
sealed interface LongTonePhase {
    data object Idle : LongTonePhase

    data class Countdown(val secondsLeft: Int) : LongTonePhase

    data object Running : LongTonePhase

    data class Finished(val result: LongToneResult) : LongTonePhase

    data class Failed(val error: AudioCaptureError) : LongTonePhase
}

/**
 * Summary shown after an exercise. Deliberately about steadiness, not about pitch accuracy yet.
 *
 * [heldSeconds] is the length of the attempt in audio time; [soundingSeconds] is how much of it was
 * above the signal gate. Once pitch detection exists, the completion rule moves from the former to a
 * validated version of the latter - see [LongTonePhase].
 */
data class LongToneResult(
    val heldSeconds: Float,
    val targetSeconds: Int,
    val soundingSeconds: Float,
    val averageLevelDbfs: Float,
    val steadyFraction: Float,
    val completed: Boolean,
)

/** Live measurements, refreshed at the UI update rate rather than per audio frame. */
data class LongToneMeasurements(
    val rmsDbfs: Float = -120f,
    val peakDbfs: Float = -120f,
    val levelFraction: Float = 0f,
    val signalPresent: Boolean = false,
    /** Magnitudes folded into display bands, already limited to the displayed frequency range. */
    val spectrumBands: FloatArray = FloatArray(0),
    val displayMaxFrequencyHz: Float = 0f,
) {
    // Array field: identity comparison would be wrong for a state class, so compare contents.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LongToneMeasurements) return false
        return rmsDbfs == other.rmsDbfs &&
            peakDbfs == other.peakDbfs &&
            levelFraction == other.levelFraction &&
            signalPresent == other.signalPresent &&
            displayMaxFrequencyHz == other.displayMaxFrequencyHz &&
            spectrumBands.contentEquals(other.spectrumBands)
    }

    override fun hashCode(): Int {
        var result = rmsDbfs.hashCode()
        result = 31 * result + peakDbfs.hashCode()
        result = 31 * result + levelFraction.hashCode()
        result = 31 * result + signalPresent.hashCode()
        result = 31 * result + displayMaxFrequencyHz.hashCode()
        result = 31 * result + spectrumBands.contentHashCode()
        return result
    }
}

/**
 * Everything the long tone screen renders. Notes are stored as concert pitch and only labelled
 * through [transposition], so a change of instrument never changes what is measured.
 */
data class LongToneUiState(
    val phase: LongTonePhase = LongTonePhase.Idle,
    val targetNote: Note,
    val selectableNotes: List<Note>,
    val transposition: Transposition,
    val instrumentName: String,
    val targetDurationSeconds: Int,
    val durationOptions: List<Int>,
    val elapsedSeconds: Float = 0f,
    val measurements: LongToneMeasurements = LongToneMeasurements(),
) {
    val isBusy: Boolean get() = phase is LongTonePhase.Running || phase is LongTonePhase.Countdown

    /** Note name as the player reads it on their instrument. */
    fun writtenLabel(note: Note): String = transposition.writtenFor(note).label

    val progress: Float
        get() = if (targetDurationSeconds <= 0) {
            0f
        } else {
            (elapsedSeconds / targetDurationSeconds).coerceIn(0f, 1f)
        }
}

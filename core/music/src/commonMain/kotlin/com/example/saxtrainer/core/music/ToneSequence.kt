package com.example.saxtrainer.core.music

/** One note of a sequence, with its length in musical time. */
data class ToneStep(
    val note: Note,
    val duration: Beats,
) {
    init {
        require(duration > Beats.ZERO) { "a step must last at least some beats" }
    }
}

/**
 * An ordered series of notes with their durations - the musical material of an exercise.
 *
 * Carries no tempo: the same material is practised at many speeds, so tempo belongs to whoever runs
 * the exercise, not to the material itself. What is written down here is what a printed exercise
 * would show: the notes, their lengths, how bars are grouped, and how altered pitches are spelled.
 *
 * It does **not** say whether its notes are concert or written pitch - that is the owner's business,
 * and the owners differ on purpose: long tones measure sound, so they hold concert pitch, while the
 * fingering and sequence trainers show what a player reads, so they hold written pitch and transpose
 * only when comparing against a microphone.
 *
 * Repetition needs no special support: ten times the same note is ten identical steps.
 */
data class ToneSequence(
    val steps: List<ToneStep>,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    /**
     * How altered pitches are spelled. F major is written with a B flat, not an A sharp, and that is
     * a property of the music rather than of whatever screen happens to draw it.
     */
    val accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    val name: String? = null,
    val description: String? = null,
) {
    init {
        require(steps.isNotEmpty()) { "a sequence needs at least one note" }
    }

    val notes: List<Note> get() = steps.map { it.note }

    val totalBeats: Beats
        get() = steps.fold(Beats.ZERO) { total, step -> total + step.duration }

    /** Beats elapsed before the step at [index] starts. */
    fun startBeatOf(index: Int): Beats {
        require(index in steps.indices) { "no step at index $index" }
        return steps.take(index).fold(Beats.ZERO) { total, step -> total + step.duration }
    }

    /** Bar and beat at which the step at [index] starts. */
    fun positionOf(index: Int): BarPosition = timeSignature.positionAt(startBeatOf(index))
}

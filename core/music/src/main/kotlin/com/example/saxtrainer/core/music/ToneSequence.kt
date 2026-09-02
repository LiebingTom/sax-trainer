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
 * would show: the notes, their lengths, and how bars are grouped.
 *
 * Repetition needs no special support: ten times the same note is ten identical steps.
 */
data class ToneSequence(
    val steps: List<ToneStep>,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
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

package com.example.saxtrainer.core.music

/**
 * One event of a sequence, with its length in musical time: a note, or a silence.
 *
 * A rest is a step with no note. Modelled that way rather than as a type of its own because a rest
 * is not a different kind of thing to a reader or to a clock - it takes its beats, it fills its part
 * of a bar, and the cursor walks onto it like anything else. The only difference is that there is no
 * pitch to play, hear or grade, and a null [note] is exactly that fact.
 */
data class ToneStep(
    /** The pitch to play, or null for a rest. */
    val note: Note?,
    val duration: Beats,
) {
    init {
        require(duration > Beats.ZERO) { "a step must last at least some beats" }
    }

    val isRest: Boolean get() = note == null

    companion object {
        /** A silence of [duration]. */
        fun rest(duration: Beats): ToneStep = ToneStep(note = null, duration = duration)
    }
}

/**
 * The pitch of a step that is known to have one.
 *
 * For material built out of notes - long tones, the fingering drill - where a rest cannot occur and
 * a null would only ever be a bug in whoever built the sequence.
 */
val ToneStep.soundedNote: Note
    get() = requireNotNull(note) { "this step is a rest, and a rest has no note to sound" }

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

    /** The notes to be played, rests left out: a rest has no pitch to be one of these. */
    val notes: List<Note> get() = steps.mapNotNull { it.note }

    val totalBeats: Beats
        get() = steps.fold(Beats.ZERO) { total, step -> total + step.duration }

    /** Beats elapsed before the step at [index] starts. */
    fun startBeatOf(index: Int): Beats {
        require(index in steps.indices) { "no step at index $index" }
        return steps.take(index).fold(Beats.ZERO) { total, step -> total + step.duration }
    }

    /** Bar and beat at which the step at [index] starts. */
    fun positionOf(index: Int): BarPosition = timeSignature.positionAt(startBeatOf(index))

    companion object {

        /**
         * One note, written as [notated].
         *
         * A single target note is a line of music one note long. Saying it that way rather than
         * inventing a type for it means the note is drawn by the same engine as everything else,
         * and comes with a written length, a clef and a place in a bar without any of them being
         * special-cased.
         */
        fun of(
            note: Note,
            notated: NotatedDuration = NotatedDuration(NoteValue.QUARTER),
            timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
            accidentalStyle: AccidentalStyle = note.conventionalStyle(),
        ): ToneSequence = ToneSequence(
            steps = listOf(ToneStep(note, notated.beats(timeSignature.beatUnit))),
            timeSignature = timeSignature,
            accidentalStyle = accidentalStyle,
        )
    }
}

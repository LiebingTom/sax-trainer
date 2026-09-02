package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep

/**
 * A practice pattern defined relative to a root note.
 *
 * Patterns store semitone [offsets] rather than fixed pitches, because the same shape is worth
 * practising from any starting note: transposing is then a different root, not a second definition.
 * Semitones keep every interval exact under transposition, so no key, mode or scale has to be
 * modelled at all.
 */
data class ExercisePattern(
    val id: String,
    val name: String,
    val description: String,
    /** Semitones above the root, one entry per note played. */
    val offsets: List<Int>,
    val defaultBeatsPerNote: Beats,
    val suggestedBpm: Int,
) {
    init {
        require(offsets.isNotEmpty()) { "a pattern needs at least one note" }
    }

    val noteCount: Int get() = offsets.size

    /** The notes this pattern produces from [root], without building a whole sequence. */
    fun notesFrom(root: Note): List<Note> = offsets.map(root::transposedBy)

    fun build(
        root: Note,
        beatsPerNote: Beats = defaultBeatsPerNote,
        timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    ): ToneSequence = ToneSequence(
        steps = notesFrom(root).map { ToneStep(it, beatsPerNote) },
        timeSignature = timeSignature,
        name = name,
        description = description,
    )
}

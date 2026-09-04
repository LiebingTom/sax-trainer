package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a guided lesson, a home-screen shortcut and a
 * "repeat that exercise" button all come through this same door - which is why nothing here, and
 * nothing in this module, mentions lessons.
 */
data class FingeringPreset(
    /** Notes the exercise draws from. Anything the chart cannot show is dropped. */
    val notes: List<Note>,
    val order: NoteOrder = NoteOrder.RANDOM,
    val secondsPerNote: Int = 5,
    val rounds: Int = 1,
    /** Whether the player has to sound each note, or only read its fingering. */
    val checkPlaying: Boolean = false,
    /** null leaves the player's own note-display preference alone. */
    val noteDisplay: NoteDisplay? = null,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) {
    init {
        require(notes.isNotEmpty()) { "a preset needs at least one note" }
        require(secondsPerNote > 0) { "a note must be shown for some time" }
        require(rounds >= 1) { "an exercise needs at least one round" }
    }
}

package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

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
    val beatsPerNote: Beats = Beats.of(5),
    /** What [beatsPerNote] is counted at, and so half of how long a note actually lasts. */
    val tempo: Tempo = Tempo(60),
    val rounds: Int = 1,
    /** Whether the player has to sound each note, or only read its fingering. */
    val checkPlaying: Boolean = false,
    /** How long a note played right stays up before the next one; zero moves on at once. */
    val successDelaySeconds: Float = 1f,
    /** Whether the note after this one is shown beside it, as its name. */
    val showNextNote: Boolean = true,
    /** null leaves the player's own note-display preference alone. */
    val noteDisplay: NoteDisplay? = null,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
    /**
     * Whether the setup screen opens filled in with this rather than being skipped.
     *
     * A launch instruction like [autoStart] and not part of the configuration: it says how the
     * screen opens, not what is practised, and neither is ever written down.
     */
    val editable: Boolean = false,
) {
    init {
        require(notes.isNotEmpty()) { "a preset needs at least one note" }
        require(beatsPerNote > Beats.ZERO) { "a note must be shown for some time" }
        require(rounds >= 1) { "an exercise needs at least one round" }
        require(successDelaySeconds >= 0f) { "a wait cannot be negative" }
    }
}

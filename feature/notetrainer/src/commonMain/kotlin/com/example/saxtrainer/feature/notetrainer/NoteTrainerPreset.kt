package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a lesson step, a shortcut and a "same again"
 * button all come through this same door - which is why nothing here mentions lessons.
 */
data class NoteTrainerPreset(
    /**
     * Notes the exercise draws from, each written as it is to be read - E flat and D sharp are
     * different entries though they sound the same. Anything the chart cannot show is dropped.
     */
    val notes: List<SpelledNote>,
    val order: NoteOrder = NoteOrder.RANDOM,
    val rounds: Int = 1,
    /** Whether the fingering is offered while the note is being asked for. */
    val showFingeringHint: Boolean = false,
    /** Whether the note's name is written under it as well as being engraved. */
    val showNoteName: Boolean = false,
    /** How long the note has to be held once it is right; zero advances as soon as it is. */
    val holdBeats: Beats = Beats.ZERO,
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
        require(rounds >= 1) { "an exercise needs at least one round" }
    }
}

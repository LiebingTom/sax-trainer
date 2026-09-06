package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a lesson step, a shortcut and a "same again"
 * button all come through this same door - which is why nothing here mentions lessons.
 */
data class NoteTrainerPreset(
    /** Notes the exercise draws from. Anything the chart cannot show is dropped. */
    val notes: List<Note>,
    val order: NoteOrder = NoteOrder.RANDOM,
    val rounds: Int = 1,
    /** Whether the fingering is offered while the note is being asked for. */
    val showFingeringHint: Boolean = false,
    /** What counts as a quick answer at this level. Scoring only; nothing times out. */
    val targetSeconds: Int = 4,
    /** How long the note has to be held once it is right; zero advances as soon as it is. */
    val holdSeconds: Double = 0.0,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) {
    init {
        require(notes.isNotEmpty()) { "a preset needs at least one note" }
        require(rounds >= 1) { "an exercise needs at least one round" }
        require(targetSeconds > 0) { "a target needs to be some length of time" }
        require(holdSeconds >= 0) { "a hold cannot be shorter than no hold at all" }
    }
}

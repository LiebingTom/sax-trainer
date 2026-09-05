package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.feature.fingering.FingeringResult
import com.example.saxtrainer.feature.notetrainer.NoteTrainerResult
import com.example.saxtrainer.feature.sequence.SequenceResult

/**
 * How the microphone trainers' results read as lesson progress.
 *
 * These live here rather than beside the steps they belong to because they name the trainers' own
 * result types, and those three trainers are Android-only until M8. The half that the catalog
 * needs - what configuration a step carries - is in `:shell`, where every platform can see it.
 */

/**
 * Reaching the last note is what completes a fingering lesson: with listening switched off the
 * trainer judges nothing, so there would otherwise be no way to finish one in a quiet room. The
 * score is only meaningful when the microphone was actually listening.
 */
fun fingeringOutcomeOf(result: FingeringResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.checked && result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)

/**
 * Notes that were played are what count. A skipped note is the only thing that costs score, which
 * is the honest reading of "did you play it" - how long it took is the player's business.
 */
fun noteTrainerOutcomeOf(result: NoteTrainerResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)

fun sequenceOutcomeOf(result: SequenceResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.checkedPitch && result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)

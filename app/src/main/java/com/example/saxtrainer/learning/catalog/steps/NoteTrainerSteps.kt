package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerResult

/** How a note trainer exercise becomes a lesson step, and how its result becomes progress. */

/** Carries a note trainer configuration through the Learning layer, which never looks inside it. */
private data class NoteTrainerLaunch(val preset: NoteTrainerPreset) : TrainerLaunch

fun noteTrainerStep(preset: NoteTrainerPreset, title: String = "") =
    LessonStep(TrainerId.NOTE_TRAINER, NoteTrainerLaunch(preset), title)

fun noteTrainerPresetOf(step: LessonStep): NoteTrainerPreset? =
    (step.launch as? NoteTrainerLaunch)?.preset

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

package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.sequence.SequencePreset
import com.example.saxtrainer.feature.sequence.SequenceResult

/** How a sequence exercise becomes a lesson step, and how its result becomes progress. */

/** Carries a sequence configuration through the Learning layer, which never looks inside it. */
private data class SequenceLaunch(val preset: SequencePreset) : TrainerLaunch

fun sequenceStep(preset: SequencePreset, title: String = "") =
    LessonStep(TrainerId.SEQUENCE, SequenceLaunch(preset), title)

fun sequencePresetOf(step: LessonStep): SequencePreset? = (step.launch as? SequenceLaunch)?.preset

fun sequenceOutcomeOf(result: SequenceResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.checkedPitch && result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)

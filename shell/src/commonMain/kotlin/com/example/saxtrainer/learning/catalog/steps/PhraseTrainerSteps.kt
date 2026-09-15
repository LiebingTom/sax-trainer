package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerPreset
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerResult

/** How a phrase trainer exercise becomes a lesson step, and how its result becomes progress. */

/** Carries a phrase trainer configuration through the Learning layer, which never looks inside it. */
private data class PhraseTrainerLaunch(val preset: PhraseTrainerPreset) : TrainerLaunch

fun phraseTrainerStep(preset: PhraseTrainerPreset, title: String = "") =
    LessonStep(TrainerId.PHRASE_TRAINER, PhraseTrainerLaunch(preset), title)

fun phraseTrainerPresetOf(step: LessonStep): PhraseTrainerPreset? = (step.launch as? PhraseTrainerLaunch)?.preset

fun phraseTrainerOutcomeOf(result: PhraseTrainerResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.plannedCount > 0) result.correctCount.toFloat() / result.plannedCount else null,
)

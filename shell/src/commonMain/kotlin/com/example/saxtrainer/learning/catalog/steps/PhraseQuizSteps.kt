package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.phrasequiz.PhraseQuizPreset
import com.example.saxtrainer.feature.phrasequiz.PhraseQuizResult

/** How a phrase quiz becomes a lesson step, and how its result becomes progress. */

/** Carries a phrase quiz configuration through the Learning layer, which never looks inside it. */
private data class PhraseQuizLaunch(val preset: PhraseQuizPreset) : TrainerLaunch

fun phraseQuizStep(preset: PhraseQuizPreset, title: String = "") =
    LessonStep(TrainerId.PHRASE_QUIZ, PhraseQuizLaunch(preset), title)

fun phraseQuizPresetOf(step: LessonStep): PhraseQuizPreset? = (step.launch as? PhraseQuizLaunch)?.preset

/** Every unit is judged, so a run always has a score to report. */
fun phraseQuizOutcomeOf(result: PhraseQuizResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.plannedCount > 0) result.correctCount.toFloat() / result.plannedCount else null,
)

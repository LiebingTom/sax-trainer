package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notequiz.QuizResult

/** How a note quiz becomes a lesson step, and how its result becomes progress. */

/** Carries a quiz configuration through the Learning layer, which never looks inside it. */
private data class NoteQuizLaunch(val preset: QuizPreset) : TrainerLaunch

fun noteQuizStep(preset: QuizPreset, title: String = "") =
    LessonStep(TrainerId.NOTE_QUIZ, NoteQuizLaunch(preset), title)

fun noteQuizPresetOf(step: LessonStep): QuizPreset? = (step.launch as? NoteQuizLaunch)?.preset

/** Every question is judged, so a quiz always has a score to report. */
fun noteQuizOutcomeOf(result: QuizResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.plannedCount > 0) {
        result.correctCount.toFloat() / result.plannedCount
    } else {
        null
    },
)

package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.fingering.FingeringPreset
import com.example.saxtrainer.feature.fingering.FingeringResult

/**
 * How a fingering exercise becomes a lesson step, and how its result becomes progress.
 *
 * One of the few files that sees both sides: `core:learning` knows nothing about a fingering
 * exercise, and `feature:fingering` knows nothing about lessons. A unit calls [fingeringStep] and
 * never touches a preset type; the trainer receives a preset and never hears the word lesson.
 */

/** Carries a fingering configuration through the Learning layer, which never looks inside it. */
private data class FingeringLaunch(val preset: FingeringPreset) : TrainerLaunch

fun fingeringStep(preset: FingeringPreset, title: String = "") =
    LessonStep(TrainerId.FINGERING, FingeringLaunch(preset), title)

/** The configuration a step carries, or null when the step belongs to a different trainer. */
fun fingeringPresetOf(step: LessonStep): FingeringPreset? = (step.launch as? FingeringLaunch)?.preset

/**
 * How a finished exercise reads as lesson progress.
 *
 * Reaching the last note is what completes a lesson: with listening switched off the trainer judges
 * nothing, so there would otherwise be no way to finish one in a quiet room. The score is only
 * meaningful when the microphone was actually listening.
 */
fun fingeringOutcomeOf(result: FingeringResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.checked && result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)

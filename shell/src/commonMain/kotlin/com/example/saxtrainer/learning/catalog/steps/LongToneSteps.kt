package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.feature.longtones.LongTonePreset
import com.example.saxtrainer.feature.longtones.LongToneResult

/** How a long tone exercise becomes a lesson step, and how its result becomes progress. */

/** Carries a long tone configuration through the Learning layer, which never looks inside it. */
private data class LongToneLaunch(val preset: LongTonePreset) : TrainerLaunch

fun longToneStep(preset: LongTonePreset, title: String = "") =
    LessonStep(TrainerId.LONG_TONES, LongToneLaunch(preset), title)

fun longTonePresetOf(step: LessonStep): LongTonePreset? =
    (step.launch as? LongToneLaunch)?.preset

/**
 * How steady the tone was is what this exercise measures, so that is what it scores.
 *
 * Pitch is measured too but decides nothing here, exactly as it decides nothing in the exercise
 * itself: a note that wanders in and out of tune was still held, and being told so is the player's
 * business rather than the curriculum's.
 */
fun longToneOutcomeOf(result: LongToneResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = result.steadyFraction.coerceIn(0f, 1f),
)

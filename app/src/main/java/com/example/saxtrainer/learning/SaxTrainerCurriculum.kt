package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.learning.catalog.fingeringLessonPath

/**
 * Everything the app teaches.
 *
 * One line per trainer that has lessons. A new trainer adds its own catalog file beside
 * `catalog/FingeringLessons.kt` and appends its path here; no existing lesson, trainer or screen is
 * touched. The [Curriculum] checks itself as it is built, so a mistake in the lesson data shows up
 * the first time the app starts rather than when a player taps a level.
 */
val SaxTrainerCurriculum: Curriculum = Curriculum(
    paths = listOf(
        fingeringLessonPath,
    ),
)

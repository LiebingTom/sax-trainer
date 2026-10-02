package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep

/**
 * C to G: recognise them.
 *
 * A unit is a topic, not a trainer. The lessons are provisional and will be rewritten; for now each
 * one runs a single practice.
 *
 * `requires` is left null throughout, so every lesson can be opened while the catalog is still
 * being written; turning progression on later is a change to this data and to nothing else.
 */
const val FIRST_NOTES_UNIT_ID = "first-notes"

val firstNotesUnit = LearningUnit(
    id = FIRST_NOTES_UNIT_ID,
    title = "Your first five notes",
    description = "C to G: read them.",
    lessons = listOf(
        Lesson(
            id = LessonId("first-notes-read"),
            title = "Read them",
            subtitle = "Which grip is this note?",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = FIRST_NOTES,
                        mode = QuizMode.NOTE_TO_FINGERING,
                        questionCount = 10,
                    ),
                ),
            ),
        ),
    ),
)

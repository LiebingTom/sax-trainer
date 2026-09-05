package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.learning.catalog.steps.fingeringStep
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * C to G: recognise them, finger them, play them.
 *
 * A unit is a topic, not a trainer. These four lessons use three different trainers because that is
 * what learning five notes takes, and the last one uses two of them in a single sitting.
 *
 * `requires` is left null throughout, so every lesson can be opened while the catalog is still
 * being written; turning progression on later is a change to this data and to nothing else.
 */
const val FIRST_NOTES_UNIT_ID = "first-notes"

val firstNotesUnit = LearningUnit(
    id = FIRST_NOTES_UNIT_ID,
    title = "Your first five notes",
    description = "C to G: read them, finger them, play them.",
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
        Lesson(
            id = LessonId("fingering-1"),
            title = "Finger them",
            subtitle = "C, D and E, in order",
            steps = listOf(
                fingeringStep(
                    notes = FIRST_THREE,
                    order = NoteOrder.ORDERED,
                    secondsPerNote = 8,
                    noteDisplay = NoteDisplay.BOTH,
                ),
            ),
        ),
        Lesson(
            id = LessonId("fingering-2"),
            title = "Finger all five",
            subtitle = "Adds F and G",
            steps = listOf(
                fingeringStep(
                    notes = FIRST_NOTES,
                    order = NoteOrder.ORDERED,
                    secondsPerNote = 5,
                    noteDisplay = NoteDisplay.BOTH,
                ),
            ),
        ),
        Lesson(
            id = LessonId("first-notes-play"),
            title = "Play them",
            subtitle = "Name the grips, then play what you read",
            steps = listOf(
                // Two trainers, one sitting: recognise the grips, then put the horn to your mouth
                // and play what is on the staff. Neither trainer knows the other one ran.
                noteQuizStep(
                    QuizPreset(
                        notes = FIRST_NOTES,
                        mode = QuizMode.FINGERING_TO_NOTE,
                        questionCount = 6,
                    ),
                    title = "Which note is this?",
                ),
                noteTrainerStep(
                    notes = FIRST_NOTES,
                    order = NoteOrder.RANDOM,
                    showFingeringHint = true,
                    targetSeconds = 8,
                    title = "Play what you read",
                ),
            ),
        ),
    ),
)

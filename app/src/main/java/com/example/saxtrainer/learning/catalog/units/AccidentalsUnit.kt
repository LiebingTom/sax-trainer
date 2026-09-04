package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.feature.fingering.FingeringPreset
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.fingeringStep
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * B flat and F sharp: the two accidentals that turn up immediately, and the two grips that are
 * nothing like the notes either side of them.
 */
const val ACCIDENTALS_UNIT_ID = "accidentals"

val accidentalsUnit = LearningUnit(
    id = ACCIDENTALS_UNIT_ID,
    title = "B flat and F sharp",
    description = "The first two accidentals, and the grips that go with them.",
    lessons = listOf(
        Lesson(
            id = LessonId("accidentals-read"),
            title = "Read them",
            subtitle = "Ten notes now, both ways round",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = ACCIDENTAL_NOTES,
                        mode = QuizMode.MIXED,
                        questionCount = 15,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("fingering-4"),
            title = "Finger them",
            subtitle = "Read it either way: name or notation, at random",
            steps = listOf(
                fingeringStep(
                    FingeringPreset(
                        notes = ACCIDENTAL_NOTES,
                        order = NoteOrder.RANDOM,
                        secondsPerNote = 5,
                        rounds = 2,
                        checkPlaying = true,
                        // Each note comes up as its letter or on the staff, decided as the exercise
                        // is built: the point of the lesson is that neither can be relied on.
                        noteDisplay = NoteDisplay.RANDOM,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("accidentals-play"),
            title = "Play them",
            subtitle = "Twice through, no hint",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = ACCIDENTAL_NOTES,
                        order = NoteOrder.RANDOM,
                        rounds = 2,
                        showFingeringHint = false,
                        targetSeconds = 4,
                    ),
                ),
            ),
        ),
    ),
)

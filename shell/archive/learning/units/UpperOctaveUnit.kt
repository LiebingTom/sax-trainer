package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * The octave above: written D5 to C6, played with the octave key.
 *
 * The topic is one key rather than nine new grips. Every note here is a grip the player already
 * owns with the octave key added - D5 is D4 and the thumb, C6 is C5 and the thumb - which is why
 * the register is read, then played with the grip shown. The reading is the other half of it: these
 * notes sit on and above the top line and are the first the player meets on ledger lines.
 */
const val UPPER_OCTAVE_UNIT_ID = "upper-octave"

val upperOctaveUnit = LearningUnit(
    id = UPPER_OCTAVE_UNIT_ID,
    title = "The octave key",
    description = "D to C above the staff: the grips you know, one octave up.",
    lessons = listOf(
        Lesson(
            id = LessonId("upper-octave-read"),
            title = "Read them",
            subtitle = "Ledger lines, both ways round",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = OCTAVE_KEY_NOTES,
                        mode = QuizMode.MIXED,
                        questionCount = 15,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("upper-octave-play"),
            title = "Play them",
            subtitle = "Mixed up, with the grip still shown",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = OCTAVE_KEY_NOTES,
                        order = NoteOrder.RANDOM,
                        // The notes are new even though the grips are not: the hint comes back for
                        // one lesson and is taken away again in the next.
                        showFingeringHint = true,
                    ),
                ),
            ),
        ),
    ),
)

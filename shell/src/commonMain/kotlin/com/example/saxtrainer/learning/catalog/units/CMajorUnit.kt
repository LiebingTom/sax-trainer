package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * C major, as a key rather than as a set of grips.
 *
 * The notes are ones the earlier units already taught; what is new is knowing them *as a scale* -
 * naming any of them on sight, finding the grip without being shown it, and holding each one long
 * enough that it is a note rather than a stab at one.
 *
 * Three lessons, on purpose: one that asks what a note is, and two that ask for it to be played -
 * the lower octave and the upper one kept apart, because the second register is a different thing
 * to blow even where it is the same grip with the octave key added.
 */
const val C_MAJOR_UNIT_ID = "c-major"

/**
 * Long enough that the note has to be held rather than merely hit.
 *
 * Four beats at sixty is four seconds, which is about as long as a beginner can hold a steady note
 * before the sound tells on them - which is the point of asking for it.
 */
private val HELD = Beats.of(4)

val cMajorUnit = LearningUnit(
    id = C_MAJOR_UNIT_ID,
    title = "C major",
    description = "The scale in both octaves: name it, then play it and hold it.",
    lessons = listOf(
        Lesson(
            id = LessonId("c-major-quiz"),
            title = "Know C major",
            subtitle = "Every note of both octaves, named and gripped",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = C_MAJOR_BOTH,
                        // Both halves of the same question: the name says what it is, the grip
                        // says how to get it, and a scale is only learned when both are there.
                        mode = QuizMode.NOTE_AND_FINGERING,
                        questionCount = 15,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("c-major-play"),
            title = "Play C major",
            subtitle = "C to C, no grips shown, four beats on each note",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = C_MAJOR_LOWER,
                        order = NoteOrder.ORDERED,
                        showFingeringHint = false,
                        holdBeats = HELD,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("c-major-play-upper"),
            title = "Play C major up top",
            subtitle = "The same scale an octave higher, held just as long",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = C_MAJOR_UPPER,
                        order = NoteOrder.ORDERED,
                        showFingeringHint = false,
                        holdBeats = HELD,
                    ),
                ),
            ),
        ),
    ),
)

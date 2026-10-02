package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * Four groups of notes, each a step on from the last, each practised the same two ways: a quiz that
 * asks for the name and the grip, and the trainer that asks for the note to be played.
 *
 * `requires` is left null throughout, so every lesson can be opened while the catalog is still
 * being written; turning progression on later is a change to this data and to nothing else.
 */
const val C_MAJOR_TWO_OCTAVES_UNIT_ID = "c-major-two-octaves"
const val D_MAJOR_NOTES_UNIT_ID = "d-major-notes"
const val CHROMATIC_SHARPS_UNIT_ID = "chromatic-sharps"
const val CHROMATIC_SHARPS_AND_FLATS_UNIT_ID = "chromatic-sharps-and-flats"

/** Questions in every group's quiz. */
internal const val QUIZ_QUESTIONS = 20

/** Times every group's notes are worked through in the trainer. */
internal const val TRAINER_ROUNDS = 3

/** How long a right note has to be held before the trainer moves on. */
internal val TRAINER_HOLD = Beats.of(2)

val cMajorTwoOctavesUnit = noteGroupUnit(
    id = C_MAJOR_TWO_OCTAVES_UNIT_ID,
    title = "C major, two octaves",
    description = "C to C to C, no sharps or flats.",
    notes = C_MAJOR_TWO_OCTAVES,
)

val dMajorNotesUnit = noteGroupUnit(
    id = D_MAJOR_NOTES_UNIT_ID,
    title = "Adding F sharp and C sharp",
    description = "The same two octaves with F♯ and C♯: everything D major needs.",
    notes = D_MAJOR_NOTES,
)

val chromaticSharpsUnit = noteGroupUnit(
    id = CHROMATIC_SHARPS_UNIT_ID,
    title = "Chromatic, with sharps",
    description = "Every note from C up to the high D, the black keys written as sharps.",
    notes = CHROMATIC_SHARPS,
)

val chromaticSharpsAndFlatsUnit = noteGroupUnit(
    id = CHROMATIC_SHARPS_AND_FLATS_UNIT_ID,
    title = "Chromatic, sharps and flats",
    description = "The same notes, every black key both as a sharp and as a flat.",
    notes = CHROMATIC_SHARPS_AND_FLATS,
)

/** One group: its quiz and its trainer, nothing more. */
private fun noteGroupUnit(
    id: String,
    title: String,
    description: String,
    notes: List<SpelledNote>,
) = LearningUnit(
    id = id,
    title = title,
    description = description,
    lessons = listOf(
        Lesson(
            id = LessonId("$id-quiz"),
            title = "Name and grip them",
            subtitle = "$QUIZ_QUESTIONS questions: the name and the fingering",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = notes,
                        mode = QuizMode.NOTE_AND_FINGERING,
                        questionCount = QUIZ_QUESTIONS,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("$id-play"),
            title = "Play them",
            subtitle = "$TRAINER_ROUNDS rounds, each note held for 2 beats",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = notes,
                        order = NoteOrder.RANDOM,
                        rounds = TRAINER_ROUNDS,
                        showFingeringHint = false,
                        holdBeats = TRAINER_HOLD,
                    ),
                ),
            ),
        ),
    ),
)

package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.feature.longtones.LongTonePreset
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.longToneStep
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * Four groups of notes, each a step on from the last, each practised the same three ways: a quiz
 * that asks for the name and the grip, the trainer that asks for the note to be played, and long
 * tones that hold each one.
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

/** How long every long tone is held: one round through the group, lowest note first. */
internal val LONG_TONE_BEATS = Beats.of(8)

val cMajorTwoOctavesUnit = noteGroupUnit(
    id = C_MAJOR_TWO_OCTAVES_UNIT_ID,
    title = "Basics 1",
    description = "C major over two octaves, C to C to C, no sharps or flats.",
    notes = C_MAJOR_TWO_OCTAVES,
)

val dMajorNotesUnit = noteGroupUnit(
    id = D_MAJOR_NOTES_UNIT_ID,
    title = "Basics 2",
    description = "The same two octaves with F♯ and C♯: everything D major needs.",
    notes = D_MAJOR_NOTES,
)

val chromaticSharpsUnit = noteGroupUnit(
    id = CHROMATIC_SHARPS_UNIT_ID,
    title = "Basics 3",
    description = "Chromatic from C to C, plus the high D, the black keys written as sharps.",
    notes = CHROMATIC_SHARPS,
)

val chromaticSharpsAndFlatsUnit = noteGroupUnit(
    id = CHROMATIC_SHARPS_AND_FLATS_UNIT_ID,
    title = "Basics 4",
    description = "The same notes as Basics 3, every black key both as a sharp and as a flat.",
    notes = CHROMATIC_SHARPS_AND_FLATS,
)

/** One group: its quiz, its trainer and its long tones, nothing more. */
private fun noteGroupUnit(
    id: String,
    title: String,
    description: String,
    notes: List<SpelledNote>,
) = LearningUnit(
    id = id,
    title = title,
    description = description,
    lessons = noteLessons(id, notes),
)

/**
 * The three ways a set of notes is learned, in order: name and grip them, play them, hold them.
 * Every unit built on a set of notes starts with these, the blues units included.
 */
internal fun noteLessons(id: String, notes: List<SpelledNote>): List<Lesson> = listOf(
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
        Lesson(
            id = LessonId("$id-long-tones"),
            title = "Hold them",
            subtitle = "One round of long tones, each note held for 8 beats",
            steps = listOf(
                longToneStep(
                    LongTonePreset(
                        notes = notes,
                        beatsPerNote = LONG_TONE_BEATS,
                    ),
                ),
            ),
        ),
)

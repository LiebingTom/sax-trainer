package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.sequence.SequencePreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep
import com.example.saxtrainer.learning.catalog.steps.sequenceStep

/**
 * The whole first octave, ending on the first thing that sounds like music.
 *
 * The same skills as the first unit, on eight notes instead of five, and then C major in time -
 * which is what the notes were for.
 */
const val OCTAVE_UNIT_ID = "octave"

val octaveUnit = LearningUnit(
    id = OCTAVE_UNIT_ID,
    title = "The whole octave",
    description = "C to C, and your first scale in time.",
    lessons = listOf(
        Lesson(
            id = LessonId("octave-read"),
            title = "Read the octave",
            subtitle = "Both ways round, notes and grips",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = OCTAVE_NOTES,
                        mode = QuizMode.MIXED,
                        questionCount = 15,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("octave-play"),
            title = "Play the octave",
            subtitle = "No hint this time: the grip is yours to remember",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = OCTAVE_NOTES,
                        order = NoteOrder.RANDOM,
                        showFingeringHint = false,
                        targetSeconds = 4,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("sequence-1"),
            title = "C major, in time",
            subtitle = "Up and down, five times, slowly, with a click",
            steps = listOf(
                sequenceStep(
                    SequencePreset(
                        sequenceId = SequenceLibrary.cMajorUp.id,
                        tempo = Tempo(70),
                        addInverse = true,
                        repeats = 5,
                        metronome = true,
                        checkPitch = true,
                        // Timing is not checked yet - one thing at a time.
                        checkTiming = false,
                    ),
                ),
            ),
        ),
    ),
)

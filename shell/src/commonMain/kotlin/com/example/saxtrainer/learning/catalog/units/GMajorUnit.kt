package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.feature.notequiz.QuizPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep

/**
 * G major, the second key, and the first one with a sharp in it.
 *
 * The same two questions as C major - what is this note, and can you play it and hold it - asked of
 * a scale that starts in the first register and crosses into the second half way up. One octave
 * only: the next one would end on G6 and the chart stops at F sharp 6.
 */
const val G_MAJOR_UNIT_ID = "g-major"

/** As long as C major asks for; a key does not change what holding a note means. */
private val HELD = Beats.of(4)
private val HOLD_TEMPO = Tempo(60)

val gMajorUnit = LearningUnit(
    id = G_MAJOR_UNIT_ID,
    title = "G major",
    description = "One octave with an F sharp in it: name it, then play it and hold it.",
    lessons = listOf(
        Lesson(
            id = LessonId("g-major-quiz"),
            title = "Know G major",
            subtitle = "All eight notes, named and gripped, F sharp included",
            steps = listOf(
                noteQuizStep(
                    QuizPreset(
                        notes = G_MAJOR,
                        mode = QuizMode.NOTE_AND_FINGERING,
                        questionCount = 12,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("g-major-play"),
            title = "Play G major",
            subtitle = "G to G, no grips shown, four beats on each note",
            steps = listOf(
                noteTrainerStep(
                    NoteTrainerPreset(
                        notes = G_MAJOR,
                        order = NoteOrder.ORDERED,
                        showFingeringHint = false,
                        targetSeconds = 6,
                        holdBeats = HELD,
                        tempo = HOLD_TEMPO,
                    ),
                ),
            ),
        ),
    ),
)

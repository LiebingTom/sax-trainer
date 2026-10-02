package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.units.ACCIDENTALS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.FIRST_NOTES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.OCTAVE_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.UPPER_OCTAVE_UNIT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.example.saxtrainer.learning.catalog.units.C_MAJOR_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.G_MAJOR_UNIT_ID
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.learning.catalog.units.C_MAJOR_BOTH

/** What each unit teaches, and in what order. */
class UnitLessonsTest {

    @Test
    fun `the units are topics, in teaching order`() {
        assertEquals(
            listOf(
                FIRST_NOTES_UNIT_ID,
                OCTAVE_UNIT_ID,
                ACCIDENTALS_UNIT_ID,
                UPPER_OCTAVE_UNIT_ID,
                C_MAJOR_UNIT_ID,
                G_MAJOR_UNIT_ID,
            ),
            SaxTrainerCurriculum.units.map { it.id },
        )
    }

    @Test
    fun `every lesson runs one practice`() {
        // Two practices in one sitting is not something the lessons do for now.
        SaxTrainerCurriculum.lessons.forEach { lesson ->
            assertEquals(1, lesson.stepCount, "${lesson.title} runs more than one practice")
        }
    }

    @Test
    fun `the first unit reads before it plays`() {
        val first = SaxTrainerCurriculum.unitOf(FIRST_NOTES_UNIT_ID)!!
        val quiz = noteQuizPresetOf(first.lessons.first().firstStep)!!

        assertEquals(QuizMode.NOTE_TO_FINGERING, quiz.mode)
        assertEquals(5, quiz.notes.size)
    }

    @Test
    fun `the octave unit ends on something that sounds like music`() {
        val octave = SaxTrainerCurriculum.unitOf(OCTAVE_UNIT_ID)!!

        assertEquals(TrainerId.SEQUENCE, octave.lessons.last().firstStep.trainerId)
    }

    @Test
    fun `every lesson says what it is`() {
        SaxTrainerCurriculum.lessons.forEach { lesson ->
            assertTrue(lesson.title.isNotBlank(), "a lesson needs a title")
            assertTrue(lesson.subtitle.isNotBlank(), "${lesson.title} needs a subtitle")
            assertNotNull(lesson.firstStep)
        }
    }

    // --- the keys ---------------------------------------------------------------------------------

    private fun unitOf(id: String) = assertNotNull(SaxTrainerCurriculum.unitOf(id), "no unit $id")

    @Test
    fun `a key is asked in two ways and no others`() {
        // Naming the note and playing it. Nothing else was asked for, and a fingering drill
        // creeping into a scale unit is exactly the kind of thing this catches.
        listOf(C_MAJOR_UNIT_ID, G_MAJOR_UNIT_ID).forEach { id ->
            val trainers = unitOf(id).lessons.flatMap { it.steps }.map { it.trainerId }.toSet()
            assertEquals(setOf(TrainerId.NOTE_QUIZ, TrainerId.NOTE_TRAINER), trainers, id)
        }
    }

    @Test
    fun `a key is quizzed on the name and the grip together`() {
        listOf(C_MAJOR_UNIT_ID, G_MAJOR_UNIT_ID).forEach { id ->
            val quiz = unitOf(id).lessons
                .flatMap { it.steps }
                .firstNotNullOf(::noteQuizPresetOf)

            assertEquals(QuizMode.NOTE_AND_FINGERING, quiz.mode, id)
        }
    }

    @Test
    fun `playing a scale is done blind, and held`() {
        listOf(C_MAJOR_UNIT_ID, G_MAJOR_UNIT_ID).forEach { id ->
            val played = unitOf(id).lessons.flatMap { it.steps }.mapNotNull(::noteTrainerPresetOf)

            assertTrue(played.isNotEmpty(), "$id never asks for a note to be played")
            played.forEach { preset ->
                assertFalse(preset.showFingeringHint, "$id shows the grip away")
                assertTrue(
                    preset.holdBeats > Beats.ZERO,
                    "$id lets a note go as soon as it is right",
                )
            }
        }
    }

    @Test
    fun `C major is taught in both octaves, one lesson each`() {
        val played = unitOf(C_MAJOR_UNIT_ID).lessons
            .flatMap { it.steps }
            .mapNotNull(::noteTrainerPresetOf)

        assertEquals(2, played.size, "the lower octave and the upper one, kept apart")
        assertEquals(
            C_MAJOR_BOTH.toSet(),
            played.flatMap { it.notes }.toSet(),
            "between them they cover the whole two octaves",
        )
    }

    @Test
    fun `G major is the key it says it is`() {
        val quiz = unitOf(G_MAJOR_UNIT_ID).lessons
            .flatMap { it.steps }
            .firstNotNullOf(::noteQuizPresetOf)

        assertEquals(8, quiz.notes.size, "one octave, G to G")
        assertTrue(
            quiz.notes.any { it.note == Note(NoteName.F_SHARP, 5) && it.token == "F#5" },
            "without the F sharp it is not G major",
        )
    }

}

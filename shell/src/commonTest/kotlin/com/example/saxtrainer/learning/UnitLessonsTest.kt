package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.LessonId
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
    fun `the upper octave unit ends across both octaves`() {
        val upper = SaxTrainerCurriculum.unitOf(UPPER_OCTAVE_UNIT_ID)!!
        val last = upper.lessons.last()

        assertEquals(2, last.stepCount)
        assertEquals(TrainerId.NOTE_TRAINER, last.stepAt(0)!!.trainerId)
        assertEquals(TrainerId.SEQUENCE, last.stepAt(1)!!.trainerId, "ending on something musical")
        assertFalse(
            noteTrainerPresetOf(last.stepAt(0)!!)!!.showFingeringHint,
            "the hint given while the register was new is taken away again",
        )
    }

    @Test
    fun `a unit uses whichever trainers its topic needs`() {
        // The point of organising by topic: one unit teaches five notes using two trainers,
        // rather than one trainer teaching every note.
        val first = SaxTrainerCurriculum.unitOf(FIRST_NOTES_UNIT_ID)!!
        val trainers = first.lessons.flatMap { it.steps }.map { it.trainerId }.toSet()

        assertEquals(setOf(TrainerId.NOTE_QUIZ, TrainerId.NOTE_TRAINER), trainers)
    }

    @Test
    fun `a lesson can run two trainers in one sitting`() {
        val lesson = SaxTrainerCurriculum.lessonOf(LessonId("first-notes-play"))!!

        assertTrue(lesson.isMultiStep)
        assertEquals(2, lesson.stepCount)
        // Name the grips first, then play what you read: the order is the teaching.
        assertEquals(TrainerId.NOTE_QUIZ, lesson.stepAt(0)!!.trainerId)
        assertEquals(TrainerId.NOTE_TRAINER, lesson.stepAt(1)!!.trainerId)
        assertTrue(lesson.stepAt(0)!!.title.isNotBlank(), "a step of a multi-step lesson should say what it is")
    }

    @Test
    fun `the first unit reads before it plays`() {
        val first = SaxTrainerCurriculum.unitOf(FIRST_NOTES_UNIT_ID)!!
        val quiz = noteQuizPresetOf(first.lessons.first().firstStep)!!

        assertEquals(QuizMode.NOTE_TO_FINGERING, quiz.mode)
        assertEquals(5, quiz.notes.size)
    }

    @Test
    fun `the hint is given while the notes are new and taken away later`() {
        val early = noteTrainerPresetOf(
            SaxTrainerCurriculum.lessonOf(LessonId("first-notes-play"))!!.stepAt(1)!!,
        )!!
        val later = noteTrainerPresetOf(
            SaxTrainerCurriculum.lessonOf(LessonId("octave-play"))!!.firstStep,
        )!!

        assertTrue(early.showFingeringHint, "the first five notes are new enough to be shown")
        assertFalse(later.showFingeringHint, "by the octave the grip is yours to remember")
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
            quiz.notes.any { it == Note(NoteName.F_SHARP, 5) },
            "without the F sharp it is not G major",
        )
    }

}

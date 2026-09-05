package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.units.ACCIDENTALS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.FIRST_NOTES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.OCTAVE_UNIT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What each unit teaches, and in what order. */
class UnitLessonsTest {

    @Test
    fun `the units are topics, in teaching order`() {
        assertEquals(
            listOf(FIRST_NOTES_UNIT_ID, OCTAVE_UNIT_ID, ACCIDENTALS_UNIT_ID),
            SaxTrainerCurriculum.units.map { it.id },
        )
    }

    @Test
    fun `a unit uses whichever trainers its topic needs`() {
        // The point of organising by topic: one unit teaches five notes using three trainers,
        // rather than one trainer teaching every note.
        val first = SaxTrainerCurriculum.unitOf(FIRST_NOTES_UNIT_ID)!!
        val trainers = first.lessons.flatMap { it.steps }.map { it.trainerId }.toSet()

        assertEquals(
            setOf(TrainerId.NOTE_QUIZ, TrainerId.FINGERING, TrainerId.NOTE_TRAINER),
            trainers,
        )
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
        assertTrue(later.targetSeconds < early.targetSeconds, "and there is less time to think about it")
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
}

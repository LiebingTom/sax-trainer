package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.units.CHROMATIC_SHARPS_AND_FLATS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.CHROMATIC_SHARPS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.C_MAJOR_TWO_OCTAVES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.D_MAJOR_NOTES_UNIT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What each group teaches, and in what order. */
class UnitLessonsTest {

    private val unitIds = listOf(
        C_MAJOR_TWO_OCTAVES_UNIT_ID,
        D_MAJOR_NOTES_UNIT_ID,
        CHROMATIC_SHARPS_UNIT_ID,
        CHROMATIC_SHARPS_AND_FLATS_UNIT_ID,
    )

    private fun unitOf(id: String) = assertNotNull(SaxTrainerCurriculum.unitOf(id), "no unit $id")

    private fun quizOf(id: String) =
        unitOf(id).lessons.flatMap { it.steps }.firstNotNullOf(::noteQuizPresetOf)

    private fun trainerOf(id: String) =
        unitOf(id).lessons.flatMap { it.steps }.firstNotNullOf(::noteTrainerPresetOf)

    private fun tokensOf(id: String) = quizOf(id).notes.map { it.token }

    @Test
    fun `the four groups are the whole curriculum, in teaching order`() {
        assertEquals(unitIds, SaxTrainerCurriculum.units.map { it.id })
    }

    @Test
    fun `each group is a note quiz and a note trainer, and nothing more`() {
        unitIds.forEach { id ->
            val unit = unitOf(id)
            assertEquals(2, unit.lessons.size, id)
            unit.lessons.forEach { assertEquals(1, it.stepCount, "${it.title} runs more than one practice") }
            assertEquals(
                listOf(TrainerId.NOTE_QUIZ, TrainerId.NOTE_TRAINER),
                unit.lessons.map { it.firstStep.trainerId },
                id,
            )
        }
    }

    @Test
    fun `every quiz asks twenty questions, name and grip together`() {
        unitIds.forEach { id ->
            val quiz = quizOf(id)
            assertEquals(20, quiz.questionCount, id)
            assertEquals(QuizMode.NOTE_AND_FINGERING, quiz.mode, id)
        }
    }

    @Test
    fun `every trainer runs three rounds, each note held for two beats, grip not shown`() {
        unitIds.forEach { id ->
            val trainer = trainerOf(id)
            assertEquals(3, trainer.rounds, id)
            assertEquals(Beats.of(2), trainer.holdBeats, id)
            assertFalse(trainer.showFingeringHint, id)
        }
    }

    @Test
    fun `a group's quiz and trainer practise the same notes`() {
        unitIds.forEach { id -> assertEquals(quizOf(id).notes, trainerOf(id).notes, id) }
    }

    @Test
    fun `the first group is C major over two octaves`() {
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5", "G5", "A5", "B5", "C6"),
            tokensOf(C_MAJOR_TWO_OCTAVES_UNIT_ID),
        )
    }

    @Test
    fun `the second adds F sharp and C sharp in both octaves, and stops at C6`() {
        val added = tokensOf(D_MAJOR_NOTES_UNIT_ID) - tokensOf(C_MAJOR_TWO_OCTAVES_UNIT_ID).toSet()

        assertEquals(listOf("C#4", "F#4", "C#5", "F#5"), added)
        assertEquals("C6", tokensOf(D_MAJOR_NOTES_UNIT_ID).last(), "the high D is left out")
    }

    @Test
    fun `the groups are called by their number`() {
        assertEquals(
            listOf("Group 1", "Group 2", "Group 3", "Group 4"),
            SaxTrainerCurriculum.units.map { it.title },
        )
    }

    @Test
    fun `the third is every note from C4 to C6 and the high D, written with sharps`() {
        val tokens = tokensOf(CHROMATIC_SHARPS_UNIT_ID)
        val pitches = quizOf(CHROMATIC_SHARPS_UNIT_ID).notes.map { it.note }

        assertEquals(26, tokens.size, "C4 up to C6, every semitone, then D6")
        assertEquals("C4", tokens.first())
        assertEquals(listOf("C6", "D6"), tokens.takeLast(2), "no C sharp between C6 and D6")
        assertEquals(tokens.size, pitches.distinct().size, "each pitch once")
        assertTrue(tokens.none { 'b' in it }, "no flats: $tokens")
        assertTrue("A#4" in tokens, "A sharp, not B flat")
    }

    @Test
    fun `the fourth is the same pitches, every black key as a sharp and as a flat`() {
        val sharps = quizOf(CHROMATIC_SHARPS_UNIT_ID).notes
        val both = quizOf(CHROMATIC_SHARPS_AND_FLATS_UNIT_ID).notes
        val tokens = both.map { it.token }

        assertEquals(sharps.map { it.note }.toSet(), both.map { it.note }.toSet())
        assertEquals(36, both.size, "26 pitches, 10 of them black keys written twice")
        listOf("C#4", "Db4", "D#4", "Eb4", "A#5", "Bb5").forEach {
            assertTrue(it in tokens, "$it is missing from $tokens")
        }
        assertTrue(tokens.none { it in setOf("C#6", "Db6") }, "no high C sharp: $tokens")
        assertTrue(
            tokens.none { it.take(2) in setOf("E#", "B#", "Fb", "Cb") },
            "white keys are written plain: $tokens",
        )
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

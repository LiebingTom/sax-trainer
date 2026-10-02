package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.asLessonOutcome
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.feature.notequiz.AnsweredQuestion
import com.example.saxtrainer.feature.notequiz.QuizResult
import com.example.saxtrainer.feature.notetrainer.NoteAttempt
import com.example.saxtrainer.feature.notetrainer.NoteTrainerResult
import com.example.saxtrainer.feature.sequence.NoteAttempt as SequenceNoteAttempt
import com.example.saxtrainer.feature.sequence.SequenceResult
import com.example.saxtrainer.learning.catalog.steps.noteQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.sequenceOutcomeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How each trainer's own result reads as lesson progress. */
class LessonOutcomeMappingTest {

    private val c4 = Note(NoteName.C, 4)
    private val writtenC4 = SpelledNote.of(c4)

    // --- the note quiz --------------------------------------------------------------------------

    private fun quiz(answered: Int, planned: Int, right: Int) = QuizResult(
        answers = List(answered) { AnsweredQuestion(writtenC4, correct = it < right) },
        plannedCount = planned,
    )

    @Test
    fun `a finished quiz is scored on the questions it promised`() {
        val outcome = noteQuizOutcomeOf(quiz(answered = 10, planned = 10, right = 8))

        assertTrue(outcome.completed)
        assertEquals(0.8f, outcome.score!!, 1e-6f)
    }

    @Test
    fun `a quiz stopped early does not complete a lesson`() {
        assertFalse(noteQuizOutcomeOf(quiz(answered = 3, planned = 10, right = 3)).completed)
    }

    // --- the note trainer ------------------------------------------------------------------------

    private fun noteTrainer(played: Int, skipped: Int, planned: Int) = NoteTrainerResult(
        attempts = List(played) { NoteAttempt(writtenC4, 1.5, wrongTries = 0, skipped = false) } +
            List(skipped) { NoteAttempt(writtenC4, null, wrongTries = 2, skipped = true) },
        plannedNoteCount = planned,
    )

    @Test
    fun `a note trainer lesson is scored on the notes that were played`() {
        // Skipping is what costs; taking a while over a note is nobody's business but the player's.
        val outcome = noteTrainerOutcomeOf(noteTrainer(played = 6, skipped = 2, planned = 8))

        assertTrue(outcome.completed)
        assertEquals(0.75f, outcome.score!!, 1e-6f)
    }

    @Test
    fun `a note trainer lesson stopped early does not complete`() {
        assertFalse(noteTrainerOutcomeOf(noteTrainer(2, skipped = 0, planned = 8)).completed)
    }

    // --- the sequence trainer ----------------------------------------------------------------------

    @Test
    fun `a sequence lesson is scored only when it was listening`() {
        val result = SequenceResult(
            sequenceName = "C Major",
            attempts = List(8) { SequenceNoteAttempt(it, c4, pitchCorrect = null, timing = null) },
            plannedNoteCount = 8,
            completed = true,
            checkedPitch = false,
            checkedTiming = false,
        )

        val outcome = sequenceOutcomeOf(result)
        assertTrue(outcome.completed)
        assertNull(outcome.score)
    }

    // --- a lesson of several steps ------------------------------------------------------------------

    @Test
    fun `a two-step lesson is finished only when both steps are, and scored on both`() {
        val quizStep = noteQuizOutcomeOf(quiz(answered = 6, planned = 6, right = 6))
        val playStep = noteTrainerOutcomeOf(noteTrainer(played = 4, skipped = 1, planned = 5))

        val lesson = listOf(quizStep, playStep).asLessonOutcome()

        assertTrue(lesson.completed)
        assertEquals((1f + 0.8f) / 2f, lesson.score!!, 1e-6f)
    }

    @Test
    fun `walking out half way through a two-step lesson finishes nothing`() {
        val lesson = listOf(
            noteQuizOutcomeOf(quiz(answered = 6, planned = 6, right = 6)),
            ExerciseOutcome(completed = false),
        ).asLessonOutcome()

        assertFalse(lesson.completed)
    }
}

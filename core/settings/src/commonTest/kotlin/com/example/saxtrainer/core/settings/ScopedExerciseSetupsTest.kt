package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Edits kept per lesson step, and how they survive the app being closed in the background. */
class ScopedExerciseSetupsTest {

    private val quizEdit = exerciseSetup { put("mode", "NOTE_TO_NAME"); put("all_fingerings", true) }
    private val trainerEdit = exerciseSetup { put("rounds", 3) }

    @Test
    fun `each scope keeps its own setup`() {
        val edits = ScopedExerciseSetups()

        edits.repositoryFor("lesson-a#0").remember(Exercise.NOTE_QUIZ, quizEdit)
        edits.repositoryFor("lesson-b#0").remember(Exercise.NOTE_QUIZ, trainerEdit)

        assertEquals(quizEdit, edits.setupOf("lesson-a#0"))
        assertEquals(trainerEdit, edits.setupOf("lesson-b#0"))
        assertEquals(quizEdit, edits.repositoryFor("lesson-a#0").setupOf(Exercise.NOTE_QUIZ))
        assertTrue(edits.setupOf("lesson-c#0").isEmpty, "a scope never edited reads as empty")
    }

    @Test
    fun `the latest edit of an exercise is what any scope last gave it`() {
        val edits = ScopedExerciseSetups()

        edits.repositoryFor("lesson-a#0").remember(Exercise.NOTE_QUIZ, quizEdit)
        edits.repositoryFor("lesson-b#0").remember(Exercise.NOTE_QUIZ, trainerEdit)

        assertEquals(trainerEdit, edits.latestByExercise.setupOf(Exercise.NOTE_QUIZ))
        assertTrue(edits.latestByExercise.setupOf(Exercise.NOTE_TRAINER).isEmpty)
    }

    @Test
    fun `everything survives save and restore`() {
        val edits = ScopedExerciseSetups()
        edits.repositoryFor("lesson-a#0").remember(Exercise.NOTE_QUIZ, quizEdit)
        edits.repositoryFor("lesson-b#1").remember(Exercise.NOTE_TRAINER, trainerEdit)

        val restored = ScopedExerciseSetups.restore(edits.save())

        assertEquals(quizEdit, restored.setupOf("lesson-a#0"))
        assertEquals(trainerEdit, restored.setupOf("lesson-b#1"))
        assertEquals(quizEdit, restored.latestByExercise.setupOf(Exercise.NOTE_QUIZ))
        assertEquals(trainerEdit, restored.latestByExercise.setupOf(Exercise.NOTE_TRAINER))
    }

    @Test
    fun `restore leaves out what it cannot read`() {
        val restored = ScopedExerciseSetups.restore(
            listOf("\u0001NO_SUCH_EXERCISE", "rounds=3", "lesson-a#0", "mode=NOTE_TO_NAME", "dangling"),
        )

        assertEquals("NOTE_TO_NAME", restored.setupOf("lesson-a#0").string("mode"))
        assertTrue(Exercise.entries.all { restored.latestByExercise.setupOf(it).isEmpty })
    }

    @Test
    fun `clear forgets every edit`() {
        val edits = ScopedExerciseSetups()
        edits.repositoryFor("lesson-a#0").remember(Exercise.NOTE_QUIZ, quizEdit)

        edits.clear()

        assertTrue(edits.setupOf("lesson-a#0").isEmpty)
        assertTrue(edits.latestByExercise.setupOf(Exercise.NOTE_QUIZ).isEmpty)
    }
}

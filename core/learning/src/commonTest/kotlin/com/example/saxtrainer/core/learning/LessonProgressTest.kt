package com.example.saxtrainer.core.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonProgressTest {

    private val lesson = LessonId("f1")

    @Test
    fun `a lesson nobody has touched reports nothing`() {
        val state = LessonProgressState()

        assertEquals(LessonProgress(), state.of(lesson))
        assertFalse(state.isCompleted(lesson))
    }

    @Test
    fun `attempts accumulate`() {
        val repository = InMemoryLessonProgressRepository()

        repeat(3) { repository.record(lesson, ExerciseOutcome(completed = false)) }

        assertEquals(3, repository.progress.value.of(lesson).attempts)
        assertFalse(repository.progress.value.isCompleted(lesson))
    }

    @Test
    fun `completion sticks once earned`() {
        val repository = InMemoryLessonProgressRepository()

        repository.record(lesson, ExerciseOutcome(completed = true))
        // Walking out of a later attempt must not take the tick away again.
        repository.record(lesson, ExerciseOutcome(completed = false))

        assertTrue(repository.progress.value.isCompleted(lesson))
        assertEquals(2, repository.progress.value.of(lesson).attempts)
    }

    @Test
    fun `the best score is kept, not the last`() {
        val repository = InMemoryLessonProgressRepository()

        repository.record(lesson, ExerciseOutcome(completed = true, score = 0.8f))
        repository.record(lesson, ExerciseOutcome(completed = true, score = 0.4f))

        assertEquals(0.8f, repository.progress.value.of(lesson).bestScore!!, 1e-6f)
    }

    @Test
    fun `an unscored attempt does not erase a score`() {
        val repository = InMemoryLessonProgressRepository()

        repository.record(lesson, ExerciseOutcome(completed = true, score = 0.6f))
        // Listening was off this time, so there is nothing to judge.
        repository.record(lesson, ExerciseOutcome(completed = true, score = null))

        assertEquals(0.6f, repository.progress.value.of(lesson).bestScore!!, 1e-6f)
    }

    @Test
    fun `an exercise nobody listened to has no score`() {
        val repository = InMemoryLessonProgressRepository()

        repository.record(lesson, ExerciseOutcome(completed = true))

        assertNull(repository.progress.value.of(lesson).bestScore)
    }

    @Test
    fun `a score is a fraction`() {
        assertFailsWith<IllegalArgumentException> {
            ExerciseOutcome(completed = true, score = 1.5f)
        }
    }

    @Test
    fun `lessons are counted for a path summary`() {
        val steps = listOf(LessonStep(TrainerId.NOTE_QUIZ, object : TrainerLaunch {}))
        val lessons = listOf(
            Lesson(LessonId("l1"), "1", "", steps),
            Lesson(LessonId("l2"), "2", "", steps),
        )
        val repository = InMemoryLessonProgressRepository()
        repository.record(LessonId("l1"), ExerciseOutcome(completed = true))

        assertEquals(1, repository.progress.value.completedCount(lessons))
    }

    @Test
    fun `recording publishes a new state`() {
        val repository = InMemoryLessonProgressRepository()
        val before = repository.progress.value

        repository.record(lesson, ExerciseOutcome(completed = true))

        assertTrue(before !== repository.progress.value, "state is immutable")
        assertFalse(before.isCompleted(lesson), "the old value is untouched")
    }
}

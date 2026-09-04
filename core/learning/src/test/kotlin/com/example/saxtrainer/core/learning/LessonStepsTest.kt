package com.example.saxtrainer.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lessons made of more than one exercise, and what a whole one is worth. */
class LessonStepsTest {

    private object Launch : TrainerLaunch

    private fun step(trainer: TrainerId, title: String = "") = LessonStep(trainer, Launch, title)

    private fun lesson(vararg steps: LessonStep) = Lesson(
        id = LessonId("l"),
        title = "Lesson",
        subtitle = "",
        steps = steps.toList(),
    )

    // --- steps ---------------------------------------------------------------------------------

    @Test
    fun `a lesson of one step is not a multi-step lesson`() {
        val lesson = lesson(step(TrainerId.NOTE_QUIZ))

        assertEquals(1, lesson.stepCount)
        assertFalse(lesson.isMultiStep)
        assertEquals(lesson.firstStep, lesson.stepAt(0))
    }

    @Test
    fun `a lesson can run two trainers in turn`() {
        val lesson = lesson(
            step(TrainerId.NOTE_QUIZ, "Read them"),
            step(TrainerId.NOTE_TRAINER, "Play them"),
        )

        assertTrue(lesson.isMultiStep)
        assertEquals(TrainerId.NOTE_QUIZ, lesson.stepAt(0)!!.trainerId)
        assertEquals(TrainerId.NOTE_TRAINER, lesson.stepAt(1)!!.trainerId)
        assertEquals("Play them", lesson.stepAt(1)!!.title)
    }

    @Test
    fun `there is no step past the last one, which is how a lesson says it is over`() {
        assertNull(lesson(step(TrainerId.FINGERING)).stepAt(1))
        assertNull(lesson(step(TrainerId.FINGERING)).stepAt(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a lesson with no exercise is not a lesson`() {
        Lesson(id = LessonId("empty"), title = "", subtitle = "", steps = emptyList())
    }

    // --- what a whole lesson scored --------------------------------------------------------------

    @Test
    fun `a lesson is finished when every one of its steps is`() {
        val outcome = listOf(
            ExerciseOutcome(completed = true),
            ExerciseOutcome(completed = true),
        ).asLessonOutcome()

        assertTrue(outcome.completed)
    }

    @Test
    fun `one abandoned step means the lesson was not done`() {
        val outcome = listOf(
            ExerciseOutcome(completed = true, score = 1f),
            ExerciseOutcome(completed = false),
        ).asLessonOutcome()

        assertFalse(outcome.completed)
    }

    @Test
    fun `nothing at all is not a finished lesson`() {
        assertFalse(emptyList<ExerciseOutcome>().asLessonOutcome().completed)
    }

    @Test
    fun `the steps that were scored are averaged`() {
        val outcome = listOf(
            ExerciseOutcome(completed = true, score = 1f),
            ExerciseOutcome(completed = true, score = 0.5f),
        ).asLessonOutcome()

        assertEquals(0.75f, outcome.score!!, 1e-6f)
    }

    @Test
    fun `a step that judged nothing is left out rather than counted as zero`() {
        // A quiz followed by an exercise nobody listened to is scored on the quiz alone.
        val outcome = listOf(
            ExerciseOutcome(completed = true, score = 0.8f),
            ExerciseOutcome(completed = true, score = null),
        ).asLessonOutcome()

        assertEquals(0.8f, outcome.score!!, 1e-6f)
    }

    @Test
    fun `a lesson nobody could judge has no score`() {
        val outcome = listOf(
            ExerciseOutcome(completed = true),
            ExerciseOutcome(completed = true),
        ).asLessonOutcome()

        assertNull(outcome.score)
    }

    // --- the run in progress ----------------------------------------------------------------------

    @Test
    fun `the steps of a lesson add up to one outcome`() {
        val runs = InMemoryLessonRunRepository()
        val id = LessonId("first-notes-play")

        runs.record(id, stepIndex = 0, outcome = ExerciseOutcome(completed = true, score = 1f))
        runs.record(id, stepIndex = 1, outcome = ExerciseOutcome(completed = true, score = 0.5f))

        val outcome = runs.outcomeOf(id)
        assertTrue(outcome.completed)
        assertEquals(0.75f, outcome.score!!, 1e-6f)
    }

    @Test
    fun `two lessons do not mix`() {
        val runs = InMemoryLessonRunRepository()
        runs.record(LessonId("a"), 0, ExerciseOutcome(completed = true, score = 1f))
        runs.record(LessonId("b"), 0, ExerciseOutcome(completed = false))

        assertTrue(runs.outcomeOf(LessonId("a")).completed)
        assertFalse(runs.outcomeOf(LessonId("b")).completed)
    }

    @Test
    fun `a step done again replaces what it scored the first time`() {
        val runs = InMemoryLessonRunRepository()
        val id = LessonId("l")

        runs.record(id, 0, ExerciseOutcome(completed = false, score = 0f))
        runs.record(id, 0, ExerciseOutcome(completed = true, score = 1f))

        assertEquals(1f, runs.outcomeOf(id).score!!, 1e-6f)
    }

    @Test
    fun `clearing forgets a half-finished lesson`() {
        val runs = InMemoryLessonRunRepository()
        val id = LessonId("l")
        runs.record(id, 0, ExerciseOutcome(completed = true, score = 1f))

        runs.clear(id)

        // Half a lesson is not a lesson, so what it had done is not worth keeping.
        assertFalse(runs.outcomeOf(id).completed)
        assertNull(runs.outcomeOf(id).score)
    }
}

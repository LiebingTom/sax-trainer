package com.example.saxtrainer.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumTest {

    private object Launch : TrainerLaunch

    private fun lesson(id: String, requires: String? = null) = Lesson(
        id = LessonId(id),
        title = id,
        subtitle = "",
        steps = listOf(LessonStep(TrainerId.FINGERING, Launch)),
        requires = requires?.let(::LessonId),
    )

    private fun unit(id: String, vararg lessons: Lesson) =
        LearningUnit(id = id, title = id, description = "", lessons = lessons.toList())

    @Test
    fun `a lesson needs at least one exercise`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lesson(LessonId("empty"), "", "", steps = emptyList())
        }
    }

    @Test
    fun `lessons are found by id and by unit`() {
        val curriculum = Curriculum(listOf(unit("fingering", lesson("f1"), lesson("f2"))))

        assertEquals("f1", curriculum.lessonOf(LessonId("f1"))!!.title)
        assertEquals(2, curriculum.unitOf("fingering")!!.lessons.size)
        assertNull(curriculum.lessonOf(LessonId("nope")))
        assertNull(curriculum.unitOf("nope"))
        assertEquals(2, curriculum.lessons.size)
    }

    @Test
    fun `two lessons cannot share an id`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Curriculum(listOf(unit("a", lesson("same")), unit("b", lesson("same"))))
        }
        assertTrue(failure.message!!.contains("same"))
    }

    @Test
    fun `a prerequisite must be an earlier lesson of the same unit`() {
        // Points at nothing.
        assertThrows(IllegalArgumentException::class.java) {
            Curriculum(listOf(unit("p", lesson("l1", requires = "missing"))))
        }
        // Points forwards, which is how a cycle would start.
        assertThrows(IllegalArgumentException::class.java) {
            Curriculum(listOf(unit("p", lesson("l1", requires = "l2"), lesson("l2"))))
        }
        // Points into another unit.
        assertThrows(IllegalArgumentException::class.java) {
            Curriculum(listOf(unit("a", lesson("a1")), unit("b", lesson("b1", requires = "a1"))))
        }
        // Backwards within the unit is the one legal direction.
        Curriculum(listOf(unit("p", lesson("l1"), lesson("l2", requires = "l1"))))
    }

    @Test
    fun `a lesson without a prerequisite is always open`() {
        val curriculum = Curriculum(listOf(unit("p", lesson("l1"), lesson("l2"))))

        assertTrue(curriculum.isUnlocked(LessonId("l1"), LessonProgressState()))
        assertTrue(curriculum.isUnlocked(LessonId("l2"), LessonProgressState()))
    }

    @Test
    fun `a gated lesson opens once its prerequisite is completed`() {
        val curriculum = Curriculum(listOf(unit("p", lesson("l1"), lesson("l2", requires = "l1"))))
        val progress = InMemoryLessonProgressRepository()

        assertFalse(curriculum.isUnlocked(LessonId("l2"), progress.progress.value))

        // Giving up half way is not completing it.
        progress.record(LessonId("l1"), ExerciseOutcome(completed = false))
        assertFalse(curriculum.isUnlocked(LessonId("l2"), progress.progress.value))

        progress.record(LessonId("l1"), ExerciseOutcome(completed = true))
        assertTrue(curriculum.isUnlocked(LessonId("l2"), progress.progress.value))
    }

    @Test
    fun `an unknown lesson is not treated as locked`() {
        val curriculum = Curriculum(listOf(unit("p", lesson("l1"))))

        assertTrue(curriculum.isUnlocked(LessonId("nope"), LessonProgressState()))
    }
}

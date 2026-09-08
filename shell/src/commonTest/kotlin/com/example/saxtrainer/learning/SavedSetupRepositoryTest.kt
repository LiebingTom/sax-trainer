package com.example.saxtrainer.learning

import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.InMemoryUserLessonRepository
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.settings.plusLesson
import kotlin.test.Test
import kotlin.test.assertEquals

/** Editing a saved practice, through the same door a trainer uses for its own last setup. */
class SavedSetupRepositoryTest {

    private val first = exerciseSetup { put("tempo", 80) }
    private val second = exerciseSetup { put("tempo", 96) }

    private fun bookOfTwo() = UserLessonBook()
        .plusLesson("first", UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", first)
        .plusLesson("second", UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", first)

    @Test
    fun `a trainer opens on the saved practice rather than on the last one played`() {
        val lessons = InMemoryUserLessonRepository(bookOfTwo())
        val saved = lessons.book.value.lessons.first()

        val setups = SavedSetupRepository(lessons, saved.id)

        assertEquals(first, setups.setupOf(Exercise.NOTE_TRAINER))
    }

    @Test
    fun `every change lands in that practice, which is all saving one again means`() {
        val lessons = InMemoryUserLessonRepository(bookOfTwo())
        val saved = lessons.book.value.lessons.first()
        val other = lessons.book.value.lessons.last()

        SavedSetupRepository(lessons, saved.id).remember(Exercise.NOTE_TRAINER, second)

        assertEquals(second, lessons.book.value.lessonOf(saved.id)?.setup)
        assertEquals(first, lessons.book.value.lessonOf(other.id)?.setup, "and in no other")
    }

    @Test
    fun `a practice that has been deleted has nothing to offer and nothing to lose`() {
        val lessons = InMemoryUserLessonRepository(UserLessonBook())

        val setups = SavedSetupRepository(lessons, "l9")
        setups.remember(Exercise.NOTE_TRAINER, second)

        assertEquals(ExerciseSetup.EMPTY, setups.setupOf(Exercise.NOTE_TRAINER))
        assertEquals(emptyList(), lessons.book.value.lessons)
    }
}

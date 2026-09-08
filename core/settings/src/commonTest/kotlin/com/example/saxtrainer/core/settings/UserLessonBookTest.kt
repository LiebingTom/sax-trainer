package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules for what the player has saved: adding, renaming, reordering and deleting.
 *
 * All of it is functions from a book to a book, so none of this needs a store, a platform or a
 * screen - which is the point of keeping the rules out of [UserLessonRepository].
 */
class UserLessonBookTest {

    private val setup = exerciseSetup { put("note_set", "CUSTOM") }

    private fun bookOf(vararg names: String): UserLessonBook =
        names.fold(UserLessonBook()) { book, name ->
            book.plusLesson(name, UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", setup)
        }

    @Test
    fun `a new book already has somewhere to put things`() {
        val book = UserLessonBook()

        assertEquals(listOf(UserLessonBook.DEFAULT_GROUP), book.groups)
        assertEquals(emptyList(), book.lessons)
    }

    @Test
    fun `a saved practice keeps its name, its trainer and its setup`() {
        val book = bookOf("Ledger lines")

        val saved = assertNotNull(book.lessons.singleOrNull())
        assertEquals("Ledger lines", saved.name)
        assertEquals("NOTE_TRAINER", saved.trainerId)
        assertEquals(setup, saved.setup)
        assertEquals(UserLessonBook.DEFAULT_GROUP.id, saved.groupId)
    }

    @Test
    fun `saving goes on the end, so the order is the order they were saved in`() {
        val book = bookOf("first", "second", "third")

        assertEquals(listOf("first", "second", "third"), book.lessons.map { it.name })
    }

    @Test
    fun `a practice with no name is not saved at all`() {
        val book = UserLessonBook()
            .plusLesson("   ", UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", setup)

        assertEquals(emptyList(), book.lessons, "a name is the only thing that tells two apart")
    }

    @Test
    fun `editing a practice changes its setup and nothing else`() {
        val book = bookOf("Ledger lines")
        val id = book.lessons.first().id
        val changed = exerciseSetup { put("note_set", "BEGINNER") }

        val edited = book.withLesson(id, changed)

        assertEquals(changed, edited.lessons.first().setup)
        assertEquals("Ledger lines", edited.lessons.first().name)
    }

    @Test
    fun `moving up and down reorders one group`() {
        val book = bookOf("first", "second", "third")
        val second = book.lessons[1].id

        assertEquals(
            listOf("second", "first", "third"),
            book.movedLesson(second, by = -1).lessons.map { it.name },
        )
        assertEquals(
            listOf("first", "third", "second"),
            book.movedLesson(second, by = 1).lessons.map { it.name },
        )
    }

    @Test
    fun `moving off either end does nothing rather than leaving the group`() {
        val book = bookOf("first", "second")

        assertEquals(book, book.movedLesson(book.lessons.first().id, by = -1))
        assertEquals(book, book.movedLesson(book.lessons.last().id, by = 1))
    }

    @Test
    fun `reordering one group leaves every other group where it was`() {
        val book = bookOf("a", "b")
            .plusGroup("Week 2")
            .let { it.plusLesson("x", it.groups.last().id, "FINGERING", setup) }
            .let { it.plusLesson("y", it.groups.last().id, "FINGERING", setup) }
        val week2 = book.groups.last().id

        val moved = book.movedLesson(book.lessonsOf(week2).last().id, by = -1)

        assertEquals(listOf("a", "b"), moved.lessonsOf(UserLessonBook.DEFAULT_GROUP.id).map { it.name })
        assertEquals(listOf("y", "x"), moved.lessonsOf(week2).map { it.name })
    }

    @Test
    fun `an id freed by a delete is not handed to something that still exists`() {
        val book = bookOf("first", "second")
        val secondId = book.lessons[1].id

        val after = book.minusLesson(book.lessons[0].id)
            .plusLesson("third", UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", setup)

        assertEquals(2, after.lessons.size)
        assertEquals(2, after.lessons.map { it.id }.toSet().size, "two exercises, two ids")
        assertTrue(after.lessons.any { it.id == secondId }, "the one that was not deleted kept its id")
    }

    @Test
    fun `deleting a group takes what was saved in it`() {
        val book = bookOf("a")
            .plusGroup("Week 2")
            .let { it.plusLesson("x", it.groups.last().id, "FINGERING", setup) }

        val after = book.minusGroup(book.groups.last().id)

        assertEquals(listOf(UserLessonBook.DEFAULT_GROUP), after.groups)
        assertEquals(listOf("a"), after.lessons.map { it.name })
    }

    @Test
    fun `the last group cannot be deleted, since everything has to live somewhere`() {
        val book = bookOf("a")

        assertEquals(book, book.minusGroup(UserLessonBook.DEFAULT_GROUP.id))
    }

    @Test
    fun `groups are renamed and reordered like anything else`() {
        val book = UserLessonBook().plusGroup("Week 2")
        val week2 = book.groups.last().id

        assertEquals("Scales", book.renamedGroup(week2, "Scales").groupOf(week2)?.name)
        assertEquals(
            listOf("Week 2", UserLessonBook.DEFAULT_GROUP.name),
            book.movedGroup(week2, by = -1).groups.map { it.name },
        )
        assertEquals(book, book.movedGroup(week2, by = 1), "already last")
    }

    @Test
    fun `a group nobody has is not deleted, moved or renamed`() {
        val book = bookOf("a")

        assertNull(book.groupOf("nothing"))
        assertEquals(book, book.minusGroup("nothing"))
        assertEquals(book, book.movedGroup("nothing", by = -1))
        assertEquals(book, book.renamedGroup("nothing", "Scales"))
    }
}

package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Writing the player's saved practices down and reading them back. */
class StoredUserLessonsTest {

    private val week1 = UserLessonGroup("g1", "Week 1")

    private val book = UserLessonBook(
        groups = listOf(UserLessonBook.DEFAULT_GROUP, week1),
        lessons = listOf(
            UserLesson(
                id = "l1",
                name = "Ledger lines, slowly",
                groupId = UserLessonBook.DEFAULT_GROUP.id,
                trainerId = "NOTE_TRAINER",
                setup = exerciseSetup {
                    put("note_set", "CUSTOM")
                    putInts("custom_notes", listOf(74, 76, 77))
                    put("tempo", 96)
                },
            ),
            UserLesson(
                id = "l2",
                name = "G major over the break",
                groupId = "g1",
                trainerId = "SEQUENCE",
                setup = exerciseSetup { put("sequence", "g-major-up") },
            ),
        ),
    )

    @Test
    fun `a book survives being written down and read back, in order`() {
        assertEquals(book, StoredUserLessons.decode(StoredUserLessons.encode(book)))
    }

    @Test
    fun `an empty book reads back as an empty book`() {
        val empty = UserLessonBook()

        assertEquals(empty, StoredUserLessons.decode(StoredUserLessons.encode(empty)))
        assertEquals(empty, StoredUserLessons.decode(null))
        assertEquals(empty, StoredUserLessons.decode(""))
    }

    @Test
    fun `a setup value may hold the characters the format itself uses`() {
        val awkward = book.copy(
            lessons = listOf(
                book.lessons.first().copy(
                    setup = exerciseSetup { put("pattern", "a=b|c") },
                ),
            ),
        )

        val read = StoredUserLessons.decode(StoredUserLessons.encode(awkward))

        assertEquals("a=b|c", read.lessons.first().setup.string("pattern"))
    }

    @Test
    fun `a name is cleaned rather than losing the exercise it belongs to`() {
        val awkward = book.copy(
            lessons = listOf(book.lessons.first().copy(name = "two\nlines | one bar")),
        )

        val read = StoredUserLessons.decode(StoredUserLessons.encode(awkward))

        val saved = assertNotNull(read.lessons.singleOrNull())
        assertEquals("two lines   one bar", saved.name)
        assertEquals(awkward.lessons.first().setup, saved.setup, "the setup came through untouched")
    }

    @Test
    fun `a trainer this version has never heard of is kept`() {
        val newer = book.copy(
            lessons = listOf(book.lessons.first().copy(trainerId = "EAR_TRAINING")),
        )

        val read = StoredUserLessons.decode(StoredUserLessons.encode(newer))

        assertEquals("EAR_TRAINING", read.lessons.first().trainerId)
    }

    @Test
    fun `lines that make no sense are skipped rather than losing the rest`() {
        val stored = """
            G|g0|My lessons
            nonsense
            X|whatever|else
            L|l1|g0|FINGERING|Long tones
            S|tempo=80
            S
        """.trimIndent()

        val read = StoredUserLessons.decode(stored)

        assertEquals(listOf(UserLessonBook.DEFAULT_GROUP), read.groups)
        assertEquals("Long tones", read.lessons.single().name)
        assertEquals(80, read.lessons.single().setup.int("tempo", 0))
    }

    @Test
    fun `an exercise whose group has gone is put somewhere it can be reached`() {
        val stored = "G|g0|My lessons\nL|l1|g9|FINGERING|Orphan"

        val read = StoredUserLessons.decode(stored)

        assertEquals("g0", read.lessons.single().groupId)
        assertTrue(read.lessonsOf("g0").isNotEmpty(), "and so it is drawn under a heading")
    }
}

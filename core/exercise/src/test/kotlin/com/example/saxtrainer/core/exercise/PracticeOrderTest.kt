package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The order every note exercise works through its notes in. */
class PracticeOrderTest {

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)
    private val notes = listOf(c4, d4, e4)

    @Test
    fun `in order is the order it was given`() {
        assertEquals(notes, practiceOrder(notes, NoteOrder.ORDERED))
    }

    @Test
    fun `a round holds every note exactly once, however it is ordered`() {
        val ordered = practiceOrder(notes, NoteOrder.RANDOM, random = Random(1))

        assertEquals(notes.size, ordered.size)
        assertEquals(notes.toSet(), ordered.toSet())
    }

    @Test
    fun `more rounds is more of the same notes`() {
        val ordered = practiceOrder(notes, NoteOrder.ORDERED, rounds = 3)

        assertEquals(notes + notes + notes, ordered)
    }

    @Test
    fun `every round is complete, so nothing is practised twice as often as anything else`() {
        val ordered = practiceOrder(notes, NoteOrder.RANDOM, rounds = 4, random = Random(7))

        assertEquals(12, ordered.size)
        notes.forEach { note ->
            assertEquals("${note.label} came up", 4, ordered.count { it == note })
        }
    }

    @Test
    fun `a round never opens with the note the one before it closed on`() {
        // Mixing the notes up should not occasionally ask for the same note twice running.
        repeat(20) { seed ->
            val ordered = practiceOrder(notes, NoteOrder.RANDOM, rounds = 5, random = Random(seed))

            ordered.zipWithNext().forEach { (first, second) ->
                assertNotEquals("seed $seed repeated ${first.label}", first, second)
            }
        }
    }

    @Test
    fun `a single note has no choice but to repeat`() {
        assertEquals(listOf(c4, c4, c4), practiceOrder(listOf(c4), NoteOrder.RANDOM, rounds = 3))
    }

    @Test
    fun `the same seed gives the same practice`() {
        val first = practiceOrder(notes, NoteOrder.RANDOM, rounds = 3, random = Random(42))
        val second = practiceOrder(notes, NoteOrder.RANDOM, rounds = 3, random = Random(42))

        assertEquals(first, second)
    }

    @Test
    fun `a note asked for twice is still practised once`() {
        assertEquals(listOf(c4, d4), practiceOrder(listOf(c4, d4, c4), NoteOrder.ORDERED))
    }

    @Test
    fun `nothing selected is nothing to practise`() {
        assertTrue(practiceOrder(emptyList(), NoteOrder.RANDOM, rounds = 3).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an exercise cannot have zero rounds`() {
        practiceOrder(notes, NoteOrder.ORDERED, rounds = 0)
    }
}

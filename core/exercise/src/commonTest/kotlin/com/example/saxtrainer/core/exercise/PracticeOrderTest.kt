package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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
            assertEquals(4, ordered.count { it == note }, "${note.label} came up")
        }
    }

    @Test
    fun `a round never opens with the note the one before it closed on`() {
        // Mixing the notes up should not occasionally ask for the same note twice running.
        repeat(20) { seed ->
            val ordered = practiceOrder(notes, NoteOrder.RANDOM, rounds = 5, random = Random(seed))

            ordered.zipWithNext().forEach { (first, second) ->
                assertNotEquals(first, second, "seed $seed repeated ${first.label}")
            }
        }
    }

    @Test
    fun `two notes over many rounds never repeat, however unlucky the generator`() {
        // Two notes is where a retried shuffle is likeliest to give up and repeat; the rule is exact.
        repeat(50) { seed ->
            val ordered = practiceOrder(listOf(c4, d4), NoteOrder.RANDOM, 50, Random(seed))

            ordered.zipWithNext().forEach { (first, second) ->
                assertNotEquals(first, second, "seed $seed repeated ${first.label}")
            }
        }
    }

    @Test
    fun `a single note has no choice but to repeat`() {
        assertEquals(listOf(c4, c4, c4), practiceOrder(listOf(c4), NoteOrder.RANDOM, rounds = 3))
    }

    @Test
    fun `in order is never rearranged to avoid a repeat`() {
        // A fixed order is exactly what it was given, even where that means the same note again.
        assertEquals(listOf(c4, c4, c4), practiceOrder(listOf(c4), NoteOrder.ORDERED, rounds = 3))
        assertEquals(notes + notes, practiceOrder(notes, NoteOrder.ORDERED, rounds = 2, random = Random(3)))
    }

    // --- one note at a time -------------------------------------------------------------------------

    @Test
    fun `a random next note is never the one before it while there is another`() {
        listOf(listOf(c4, d4), notes + Note(NoteName.G, 4) + Note(NoteName.A, 4)).forEach { pool ->
            pool.forEach { previous ->
                repeat(200) { seed ->
                    val next = randomNoteAvoiding(pool, previous, Random(seed))

                    assertNotEquals(previous, next, "seed $seed repeated ${previous.label}")
                    assertTrue(next in pool, "seed $seed chose ${next.label}, which is not in the pool")
                }
            }
        }
    }

    @Test
    fun `a random next note can be any of the others`() {
        // Avoiding the last note is all it does: the rest are still chosen between at random.
        val random = Random(11)
        val drawn = (1..300).map { randomNoteAvoiding(notes, c4, random) }.toSet()

        assertEquals(setOf(d4, e4), drawn)
    }

    @Test
    fun `a pool of one note repeats it`() {
        assertEquals(c4, randomNoteAvoiding(listOf(c4), c4, Random(1)))
        assertEquals(c4, randomNoteAvoiding(listOf(c4, c4), c4, Random(1)), "one note written twice")
    }

    @Test
    fun `with nothing before it any note can come first`() {
        val random = Random(5)
        val drawn = (1..300).map { randomNoteAvoiding(notes, null, random) }.toSet()

        assertEquals(notes.toSet(), drawn)
    }

    @Test
    fun `there is no next note in an empty pool`() {
        assertFailsWith<IllegalArgumentException> { randomNoteAvoiding(emptyList<Note>(), null) }
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
        assertTrue(practiceOrder(emptyList<Note>(), NoteOrder.RANDOM, rounds = 3).isEmpty())
    }

    @Test
    fun `an exercise cannot have zero rounds`() {
        assertFailsWith<IllegalArgumentException> {
            practiceOrder(notes, NoteOrder.ORDERED, rounds = 0)
        }
    }

    // --- written notes, judged by pitch -----------------------------------------------------------

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `two spellings of one pitch are never asked for back to back while anything else is left`() {
        val pool = listOf("C4", "D#4", "Eb4", "F4", "Gb4", "F#4").map(::written)

        repeat(100) { seed ->
            val order = practiceOrder(pool, NoteOrder.RANDOM, rounds = 3, random = Random(seed)) { it.note }

            assertEquals(pool.size * 3, order.size, "every written note, every round")
            order.zipWithNext().forEach { (first, second) ->
                assertNotEquals(first.note, second.note, "seed $seed played $first then $second: one key twice")
            }
        }
    }

    @Test
    fun `nothing but one pitch in two spellings still ends`() {
        val pool = listOf(written("D#4"), written("Eb4"))

        val order = practiceOrder(pool, NoteOrder.RANDOM, rounds = 5, random = Random(3)) { it.note }

        assertEquals(10, order.size, "nothing else to put between them, so they follow each other")
        assertEquals(pool.toSet(), order.toSet())
    }

    @Test
    fun `without shared pitches the shuffle is exactly the one it always was`() {
        val pool = listOf(c4, d4, e4)

        repeat(20) { seed ->
            assertEquals(
                practiceOrder(pool, NoteOrder.RANDOM, rounds = 4, random = Random(seed)),
                practiceOrder(pool.map { SpelledNote.of(it) }, NoteOrder.RANDOM, rounds = 4, random = Random(seed)) { it.note }
                    .map { it.note },
            )
        }
    }

    @Test
    fun `the next note drawn is never the previous pitch respelled`() {
        val pool = listOf(written("D#4"), written("Eb4"), written("F4"))

        repeat(30) { seed ->
            assertEquals(written("F4"), randomNoteAvoiding(pool, written("Eb4"), Random(seed)) { it.note })
        }
    }
}

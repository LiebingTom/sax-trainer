package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Tempo
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringExerciseGeneratorTest {

    private val tempo = Tempo(60)
    private val notes = listOf(
        Note(NoteName.C, 4),
        Note(NoteName.D, 4),
        Note(NoteName.E, 4),
        Note(NoteName.F, 4),
        Note(NoteName.G, 4),
    )

    private fun generate(
        selection: List<Note> = notes,
        order: NoteOrder = NoteOrder.RANDOM,
        seconds: Int = 5,
        rounds: Int = 1,
        random: Random = Random(seed = 1),
    ) = FingeringExerciseGenerator.generate(
        notes = selection,
        order = order,
        secondsPerNote = seconds,
        rounds = rounds,
        tempo = tempo,
        random = random,
    )

    @Test
    fun `ordered mode keeps the selected order`() {
        assertEquals(notes, generate(order = NoteOrder.ORDERED).notes)
    }

    @Test
    fun `random mode plays every selected note exactly once`() {
        val played = generate(order = NoteOrder.RANDOM).notes

        assertEquals(notes.size, played.size)
        assertEquals(notes.toSet(), played.toSet())
        assertEquals(played.size, played.distinct().size)
    }

    @Test
    fun `random mode differs from the selection order`() {
        // With five notes a shuffle equal to the input is possible but not for this seed.
        assertNotEquals(notes, generate(order = NoteOrder.RANDOM, random = Random(seed = 7)).notes)
    }

    @Test
    fun `the same seed gives the same exercise and different seeds do not`() {
        val first = generate(random = Random(seed = 3)).notes
        val second = generate(random = Random(seed = 3)).notes
        val other = generate(random = Random(seed = 4)).notes

        assertEquals(first, second)
        assertNotEquals(first, other)
    }

    @Test
    fun `the selection is not modified`() {
        val selection = notes.toMutableList()
        val before = selection.toList()

        generate(selection = selection, order = NoteOrder.RANDOM)

        assertEquals(before, selection)
    }

    @Test
    fun `rounds repeat the whole pool`() {
        val played = generate(order = NoteOrder.RANDOM, rounds = 3).notes

        assertEquals(notes.size * 3, played.size)
        notes.forEach { note ->
            assertEquals("$note should appear once per round", 3, played.count { it == note })
        }
    }

    @Test
    fun `a note never immediately repeats across a round boundary`() {
        repeat(50) { seed ->
            val played = generate(order = NoteOrder.RANDOM, rounds = 4, random = Random(seed)).notes
            played.zipWithNext().forEach { (first, second) ->
                assertNotEquals("seed $seed produced a repeat", first, second)
            }
        }
    }

    @Test
    fun `a single note simply repeats`() {
        val single = listOf(Note(NoteName.G, 4))
        val played = generate(selection = single, rounds = 3).notes

        assertEquals(List(3) { single.single() }, played)
    }

    @Test
    fun `duplicates in the selection are ignored`() {
        val played = generate(selection = notes + notes.first(), order = NoteOrder.ORDERED).notes

        assertEquals(notes, played)
    }

    @Test
    fun `note length follows the chosen seconds at the reference tempo`() {
        val threeSeconds = generate(seconds = 3).steps.first().duration
        val eightSeconds = generate(seconds = 8).steps.first().duration

        assertEquals(tempo.beatsFor(3.0), threeSeconds)
        assertEquals(Beats.of(3), threeSeconds)
        assertEquals(Beats.of(8), eightSeconds)
        // The conversion is the shared one, so the sequence is usable by anything musical.
        assertEquals(3.0, tempo.secondsFor(threeSeconds), 1e-9)
    }

    @Test
    fun `the exercise is named for the player`() {
        assertEquals("Fingering practice", generate().name)
    }

    @Test
    fun `an empty selection is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { generate(selection = emptyList()) }
    }

    @Test
    fun `nonsensical settings are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { generate(rounds = 0) }
        assertThrows(IllegalArgumentException::class.java) { generate(seconds = 0) }
    }

    @Test
    fun `every step carries the same length`() {
        val steps = generate(seconds = 5, rounds = 2).steps

        assertTrue(steps.all { it.duration == Beats.of(5) })
        assertEquals(50.0, generate(seconds = 5, rounds = 2).totalBeats.value, 1e-9)
    }
}

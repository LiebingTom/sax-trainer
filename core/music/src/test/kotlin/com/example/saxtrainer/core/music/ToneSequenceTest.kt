package com.example.saxtrainer.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToneSequenceTest {

    private fun step(name: NoteName, octave: Int, beats: Int) =
        ToneStep(Note(name, octave), Beats.of(beats))

    private val sequence = ToneSequence(
        steps = listOf(
            step(NoteName.A, 4, 4),
            step(NoteName.B, 4, 4),
            step(NoteName.C, 5, 2),
        ),
    )

    @Test
    fun `total length is the sum of the note lengths`() {
        assertEquals(10.0, sequence.totalBeats.value, 1e-9)
    }

    @Test
    fun `each note starts after the ones before it`() {
        assertEquals(0.0, sequence.startBeatOf(0).value, 1e-9)
        assertEquals(4.0, sequence.startBeatOf(1).value, 1e-9)
        assertEquals(8.0, sequence.startBeatOf(2).value, 1e-9)
    }

    @Test
    fun `note positions fall in the right bar`() {
        assertEquals(BarPosition(0, 0.0), sequence.positionOf(0))
        assertEquals(BarPosition(1, 0.0), sequence.positionOf(1))
        assertEquals(BarPosition(2, 0.0), sequence.positionOf(2))
    }

    @Test
    fun `notes are exposed in order`() {
        assertEquals(
            listOf(Note(NoteName.A, 4), Note(NoteName.B, 4), Note(NoteName.C, 5)),
            sequence.notes,
        )
    }

    @Test
    fun `a sequence carries no tempo`() {
        // Compile-time guarantee in effect: the same material is timed by whoever plays it.
        val slow = Tempo(40).secondsFor(sequence.totalBeats)
        val fast = Tempo(120).secondsFor(sequence.totalBeats)

        assertEquals(15.0, slow, 1e-9)
        assertEquals(5.0, fast, 1e-9)
    }

    @Test
    fun `repetition is just repeated notes`() {
        val repeated = ToneSequence(List(10) { step(NoteName.A, 4, 4) })

        assertEquals(10, repeated.steps.size)
        assertEquals(40.0, repeated.totalBeats.value, 1e-9)
        assertEquals(1, repeated.notes.distinct().size)
    }

    @Test
    fun `an empty sequence is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ToneSequence(emptyList()) }
    }

    @Test
    fun `a note of no length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToneStep(Note(NoteName.A, 4), Beats.ZERO)
        }
    }

    @Test
    fun `asking for a note outside the sequence fails`() {
        assertThrows(IllegalArgumentException::class.java) { sequence.startBeatOf(3) }
    }
}

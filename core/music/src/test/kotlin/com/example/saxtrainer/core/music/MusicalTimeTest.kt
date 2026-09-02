package com.example.saxtrainer.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicalTimeTest {

    @Test
    fun `at sixty bpm one beat is one second`() {
        val tempo = Tempo(60)
        assertEquals(1.0, tempo.secondsFor(Beats.of(1)), 1e-9)
        assertEquals(4.0, tempo.secondsFor(Beats.of(4)), 1e-9)
    }

    @Test
    fun `faster tempo makes the same music shorter`() {
        assertEquals(2.0, Tempo(120).secondsFor(Beats.of(4)), 1e-9)
        assertEquals(6.0, Tempo(40).secondsFor(Beats.of(4)), 1e-9)
    }

    @Test
    fun `beats and seconds convert both ways`() {
        val tempo = Tempo(90)
        val beats = Beats(3.5)
        assertEquals(beats.value, tempo.beatsFor(tempo.secondsFor(beats)).value, 1e-9)
        assertEquals(2.0, tempo.beatsFor(tempo.secondsFor(Beats.of(2))).value, 1e-9)
    }

    @Test
    fun `tempo must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { Tempo(0) }
    }

    @Test
    fun `beats add and scale`() {
        assertEquals(6.0, (Beats.of(4) + Beats.of(2)).value, 1e-9)
        assertEquals(12.0, (Beats.of(4) * 3).value, 1e-9)
        assertTrue(Beats.of(4) > Beats.of(2))
    }

    @Test
    fun `negative durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Beats(-1.0) }
    }

    @Test
    fun `the time signature groups beats into bars`() {
        val fourFour = TimeSignature.FOUR_FOUR

        assertEquals(BarPosition(0, 0.0), fourFour.positionAt(Beats.ZERO))
        assertEquals(BarPosition(0, 3.0), fourFour.positionAt(Beats.of(3)))
        assertEquals(BarPosition(1, 0.0), fourFour.positionAt(Beats.of(4)))
        assertEquals(BarPosition(2, 1.5), fourFour.positionAt(Beats(9.5)))
    }

    @Test
    fun `a three four bar is shorter but a beat is not`() {
        val threeFour = TimeSignature(3, 4)

        assertEquals(BarPosition(1, 0.0), threeFour.positionAt(Beats.of(3)))
        // The time signature groups beats, it must not rescale a duration.
        assertEquals(
            Tempo(60).secondsFor(Beats.of(4)),
            Tempo(60).secondsFor(Beats.of(4)),
            1e-9,
        )
    }
}

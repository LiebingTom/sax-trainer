package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CentsTest {

    @Test
    fun `identical frequencies are zero cents apart`() {
        assertEquals(0.0, Cents.between(440.0, 440.0), 1e-9)
    }

    @Test
    fun `an octave is twelve hundred cents`() {
        assertEquals(1200.0, Cents.between(880.0, 440.0), 1e-9)
        assertEquals(-1200.0, Cents.between(220.0, 440.0), 1e-9)
    }

    @Test
    fun `a semitone is one hundred cents`() {
        val aSharp4 = Note(NoteName.A_SHARP, 4).frequencyHz()
        assertEquals(100.0, Cents.between(aSharp4, 440.0), 1e-6)
    }

    @Test
    fun `sharp is positive and flat is negative`() {
        assertTrue(Cents.between(442.0, 440.0) > 0)
        assertTrue(Cents.between(438.0, 440.0) < 0)
        assertEquals(7.85, Cents.between(442.0, 440.0), 0.01)
    }

    @Test
    fun `shift moves a frequency by the requested amount`() {
        val sharp = Cents.shift(440.0, 10.0)
        assertEquals(10.0, Cents.between(sharp, 440.0), 1e-9)

        val flat = Cents.shift(440.0, -25.0)
        assertEquals(-25.0, Cents.between(flat, 440.0), 1e-9)
    }

    @Test
    fun `shift and between are inverse`() {
        val target = Note(NoteName.G, 3).frequencyHz()
        val played = Cents.shift(target, 33.3)
        assertEquals(33.3, Cents.between(played, target), 1e-9)
    }
}

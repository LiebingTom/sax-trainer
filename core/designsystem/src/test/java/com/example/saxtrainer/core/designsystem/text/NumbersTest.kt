package com.example.saxtrainer.core.designsystem.text

import org.junit.Assert.assertEquals
import org.junit.Test

class NumbersTest {

    // ---------------------------------------------------------------- toFixed

    @Test
    fun `keeps the asked-for number of digits`() {
        assertEquals("440.0", 440.0.toFixed(1))
        assertEquals("2.50", 2.5.toFixed(2))
        assertEquals("3", 3.4.toFixed(0))
    }

    @Test
    fun `pads a short fraction rather than truncating it`() {
        assertEquals("1.05", 1.05.toFixed(2))
        assertEquals("1.00", 1.0.toFixed(2))
    }

    @Test
    fun `rounds a tie away from zero, as a reader expects`() {
        // Ties that a double can actually hold exactly - 0.125 and 2.5 are representable, 2.45 is
        // not, and asserting on one that is not would be testing binary floating point, not this.
        assertEquals("0.13", 0.125.toFixed(2))
        assertEquals("-0.13", (-0.125).toFixed(2))
        assertEquals("3", 2.5.toFixed(0))
    }

    @Test
    fun `rounding can carry into the whole part`() {
        assertEquals("1.0", 0.96.toFixed(1))
        assertEquals("10.0", 9.99.toFixed(1))
    }

    @Test
    fun `always writes the decimal separator as a dot`() {
        // The point of the helper: never the platform locale's comma.
        assertEquals("0.1", 0.1.toFixed(1))
    }

    @Test
    fun `a negative that rounds to zero is not written as minus zero`() {
        assertEquals("0.0", (-0.01).toFixed(1))
    }

    @Test
    fun `formats floats the same way`() {
        assertEquals("1.5", 1.5f.toFixed(1))
    }

    @Test
    fun `an unreadable value is a dash rather than NaN`() {
        assertEquals("-", Double.NaN.toFixed(1))
    }

    // ---------------------------------------------------------------- minutesAndSeconds

    @Test
    fun `pads the seconds to two digits`() {
        assertEquals("1:05", minutesAndSeconds(65))
        assertEquals("0:00", minutesAndSeconds(0))
    }

    @Test
    fun `minutes are not padded and may pass ten`() {
        assertEquals("12:30", minutesAndSeconds(12 * 60 + 30))
    }
}

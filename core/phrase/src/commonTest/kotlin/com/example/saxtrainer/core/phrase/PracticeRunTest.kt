package com.example.saxtrainer.core.phrase

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PracticeRunTest {

    private val units = listOf(PracticeUnit(0..0), PracticeUnit(1..1), PracticeUnit(2..2))

    @Test
    fun notMixedRepeatsTheWholePassInOrder() {
        val run = units.repeated(repeats = 3, mixed = false)

        assertEquals(units + units + units, run)
    }

    @Test
    fun oneRepeatIsJustTheUnitsThemselves() {
        assertEquals(units, units.repeated(repeats = 1, mixed = false))
        assertEquals(units.size, units.repeated(repeats = 1, mixed = true, random = Random(1)).size)
    }

    @Test
    fun mixedDrawsFromOnePoolOfEveryRepeat() {
        val run = units.repeated(repeats = 3, mixed = true, random = Random(1))

        // Every one of the 3 x 3 slots is filled, each exactly as many times as it was asked for -
        // shuffled together rather than three untouched laps back to back.
        assertEquals(9, run.size)
        assertEquals(mapOf(units[0] to 3, units[1] to 3, units[2] to 3), run.groupingBy { it }.eachCount())
        assertNotEquals(units + units + units, run, "a real shuffle should not reproduce the unmixed order")
    }
}

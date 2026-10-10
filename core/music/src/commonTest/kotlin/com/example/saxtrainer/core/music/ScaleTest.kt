package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScaleTest {

    private val MAJOR = Scale(id = "major", name = "Major", intervals = listOf(0, 2, 4, 5, 7, 9, 11))
    private val BLUES = Scale(
        id = "blues",
        name = "Blues",
        intervals = listOf(0, 3, 5, 6, 7, 10),
        letterSteps = listOf(0, 2, 3, 4, 4, 6),
    )

    @Test
    fun majorScaleDegreesAreWholeAndHalfSteps() {
        val expected = listOf(0, 2, 4, 5, 7, 9, 11)
        expected.forEachIndexed { index, offset ->
            assertEquals(offset, MAJOR.semitoneOffset(index + 1))
        }
    }

    @Test
    fun degreePastTheTopWrapsIntoTheNextOctave() {
        assertEquals(12, MAJOR.semitoneOffset(8))
        assertEquals(14, MAJOR.semitoneOffset(9))
    }

    @Test
    fun degreeBelowOneWrapsIntoTheOctaveBelow() {
        assertEquals(-1, MAJOR.semitoneOffset(0))
        assertEquals(-3, MAJOR.semitoneOffset(-1))
        assertEquals(-12, MAJOR.semitoneOffset(-6))
    }

    @Test
    fun aScaleMustStartOnItsRoot() {
        assertFailsWith<IllegalArgumentException> {
            Scale(id = "bad", name = "Bad", intervals = listOf(2, 4, 5))
        }
    }

    @Test
    fun aSevenNoteScaleWritesEachDegreeOnTheNextLetter() {
        assertEquals((0..6).toList(), (1..7).map { MAJOR.letterStep(it) })
        assertEquals(7, MAJOR.letterStep(8), "the octave is the root's letter again, one up")
    }

    @Test
    fun theBluesSaysWhichLetterEachOfItsSixDegreesIsWrittenOn() {
        assertEquals(listOf(0, 2, 3, 4, 4, 6), (1..6).map { BLUES.letterStep(it) })
        assertEquals(7, BLUES.letterStep(7))
    }

    @Test
    fun aScaleOfAnotherSizeSaysNothingAboutSpellingUnlessTold() {
        val wholeTone = Scale(id = "wt", name = "Whole tone", intervals = listOf(0, 2, 4, 6, 8, 10))
        assertEquals(null, wholeTone.letterStep(1))
        assertFailsWith<IllegalArgumentException> {
            Scale(id = "bad", name = "Bad", intervals = listOf(0, 3, 7), letterSteps = listOf(0, 2))
        }
    }
}

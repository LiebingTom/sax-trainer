package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

/** Notes counted from the root against the major scale, the way an improviser names them. */
class ScaleDegreeTest {

    private val major = Scale("major", "Major", listOf(0, 2, 4, 5, 7, 9, 11))
    private val minor = Scale("minor", "Minor", listOf(0, 2, 3, 5, 7, 8, 10))
    private val blues = Scale("blues", "Blues", listOf(0, 3, 5, 6, 7, 10), letterSteps = listOf(0, 2, 3, 4, 4, 6))
    private val pentatonic = Scale("major_pentatonic", "Major pentatonic", listOf(0, 2, 4, 7, 9), letterSteps = listOf(0, 1, 2, 4, 5))
    private val unspelled = Scale("six", "Six", listOf(0, 2, 4, 6, 8, 10))

    private fun labels(scale: Scale) = (1..scale.degreeCount).map { scale.degreeOf(scale.semitoneOffset(it)).label }

    @Test
    fun `major counts 1 to 7`() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7"), labels(major))
    }

    @Test
    fun `minor flattens 3 6 and 7`() {
        assertEquals(listOf("1", "2", "♭3", "4", "5", "♭6", "♭7"), labels(minor))
    }

    @Test
    fun `the blues reads flat 3 4 flat 5 5 flat 7, the flat five on the five`() {
        assertEquals(listOf("1", "♭3", "4", "♭5", "5", "♭7"), labels(blues))
    }

    @Test
    fun `the major pentatonic reads 1 2 3 5 6`() {
        assertEquals(listOf("1", "2", "3", "5", "6"), labels(pentatonic))
    }

    @Test
    fun `an octave up and down is marked, not renumbered`() {
        assertEquals(ScaleDegree(1, octave = 1), major.degreeOf(12))
        assertEquals(ScaleDegree(7, octave = -1), major.degreeOf(-1))
        assertEquals(ScaleDegree(5, octave = -1), pentatonic.degreeOf(pentatonic.semitoneOffset(-1)))
        assertEquals(ScaleDegree(6, octave = -1), pentatonic.degreeOf(pentatonic.semitoneOffset(0)))
    }

    @Test
    fun `a note outside the scale is counted chromatically`() {
        assertEquals("♭2", major.degreeOf(1).label)
        assertEquals("♭3", major.degreeOf(3).label)
        assertEquals("4", blues.degreeOf(5).label)
        assertEquals("2", blues.degreeOf(2).label)
    }

    @Test
    fun `a scale that does not say how it is spelled is counted chromatically`() {
        assertEquals(listOf("1", "2", "3", "♭5", "♭6", "♭7"), labels(unspelled))
    }
}

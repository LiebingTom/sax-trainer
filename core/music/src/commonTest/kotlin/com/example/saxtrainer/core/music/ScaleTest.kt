package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScaleTest {

    @Test
    fun majorScaleDegreesAreWholeAndHalfSteps() {
        val expected = listOf(0, 2, 4, 5, 7, 9, 11)
        expected.forEachIndexed { index, offset ->
            assertEquals(offset, Scales.MAJOR.semitoneOffset(index + 1))
        }
    }

    @Test
    fun degreePastTheTopWrapsIntoTheNextOctave() {
        assertEquals(12, Scales.MAJOR.semitoneOffset(8))
        assertEquals(14, Scales.MAJOR.semitoneOffset(9))
    }

    @Test
    fun degreeBelowOneIsRejected() {
        assertFailsWith<IllegalArgumentException> { Scales.MAJOR.semitoneOffset(0) }
    }

    @Test
    fun aScaleMustStartOnItsRoot() {
        assertFailsWith<IllegalArgumentException> {
            Scale(id = "bad", name = "Bad", intervals = listOf(2, 4, 5))
        }
    }

    @Test
    fun byIdFindsAKnownScaleAndMissesAnUnknownOne() {
        assertEquals(Scales.MAJOR, Scales.byId("major"))
        assertEquals(Scales.BLUES, Scales.byId("blues"))
        assertEquals(null, Scales.byId("does_not_exist"))
    }
}

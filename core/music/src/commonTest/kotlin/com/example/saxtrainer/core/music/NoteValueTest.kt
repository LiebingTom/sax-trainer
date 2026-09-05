package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a duration is written down.
 *
 * The renderer asks these questions and nothing else, so if the answers are right the notation is
 * right for every note value the app can draw.
 */
class NoteValueTest {

    @Test
    fun `the plain values are recognised`() {
        assertEquals(NotatedDuration(NoteValue.WHOLE), NotatedDuration.of(Beats(4.0)))
        assertEquals(NotatedDuration(NoteValue.HALF), NotatedDuration.of(Beats(2.0)))
        assertEquals(NotatedDuration(NoteValue.QUARTER), NotatedDuration.of(Beats(1.0)))
        assertEquals(NotatedDuration(NoteValue.EIGHTH), NotatedDuration.of(Beats(0.5)))
        assertEquals(NotatedDuration(NoteValue.SIXTEENTH), NotatedDuration.of(Beats(0.25)))
    }

    @Test
    fun `a dot adds half`() {
        assertEquals(NotatedDuration(NoteValue.HALF, dots = 1), NotatedDuration.of(Beats(3.0)))
        assertEquals(NotatedDuration(NoteValue.QUARTER, dots = 1), NotatedDuration.of(Beats(1.5)))
        assertEquals(NotatedDuration(NoteValue.EIGHTH, dots = 1), NotatedDuration.of(Beats(0.75)))
    }

    @Test
    fun `two dots add a half and a quarter`() {
        // A doubly dotted quarter is 1 + 1/2 + 1/4 beats.
        assertEquals(NotatedDuration(NoteValue.QUARTER, dots = 2), NotatedDuration.of(Beats(1.75)))
    }

    @Test
    fun `a written duration and its length agree`() {
        NoteValue.entries.forEach { value ->
            (0..NotatedDuration.MAX_DOTS).forEach { dots ->
                val written = NotatedDuration(value, dots)
                assertEquals(written, NotatedDuration.of(written.beats()), written.toString())
            }
        }
    }

    @Test
    fun `the beat unit decides what a beat is worth`() {
        // In 6/8 the beat is an eighth, so one beat is written as an eighth note.
        assertEquals(
            NotatedDuration(NoteValue.EIGHTH),
            NotatedDuration.of(Beats(1.0), beatUnit = 8),
        )
        assertEquals(
            NotatedDuration(NoteValue.QUARTER),
            NotatedDuration.of(Beats(2.0), beatUnit = 8),
        )
    }

    @Test
    fun `a duration with no written form says so`() {
        // Five beats is a tie in engraved music, which this app does not write.
        assertNull(NotatedDuration.of(Beats(5.0)))
        assertNull(NotatedDuration.of(Beats(0.3)))
        assertNull(NotatedDuration.of(Beats.ZERO))
    }

    @Test
    fun `heads, stems and flags follow the value`() {
        assertFalse(NoteValue.WHOLE.isFilledHead, "a whole note is hollow")
        assertFalse(NoteValue.HALF.isFilledHead, "a half note is hollow")
        assertTrue(NoteValue.QUARTER.isFilledHead)

        assertFalse(NoteValue.WHOLE.hasStem, "a whole note has no stem")
        assertTrue(NoteValue.HALF.hasStem)

        assertEquals(0, NoteValue.QUARTER.beamCount)
        assertEquals(1, NoteValue.EIGHTH.beamCount)
        assertEquals(2, NoteValue.SIXTEENTH.beamCount)
    }

    @Test
    fun `more dots than the notation draws is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            NotatedDuration(NoteValue.QUARTER, dots = 3)
        }
    }
}

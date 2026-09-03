package com.example.saxtrainer.core.designsystem.component

import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sizing a screen reserves for a note.
 *
 * Pure geometry, so it can be checked without drawing anything: what matters is that one range holds
 * every note a screen will show, because that is what stops the layout moving as the notes change.
 */
class StaffExtentTest {

    private fun note(name: NoteName, octave: Int) = Note(name, octave)

    @Test
    fun `one note needs at least the whole staff`() {
        // A4 sits inside the staff, so the drawing is the staff plus room for the stem.
        val range = staffStepRange(listOf(note(NoteName.A, 4)))

        assertTrue("the staff must fit", range.first <= 0 && range.last >= 8)
    }

    @Test
    fun `a range holds every note it was built from`() {
        val notes = listOf(
            note(NoteName.C, 4),
            note(NoteName.G, 4),
            note(NoteName.C, 5),
            note(NoteName.F_SHARP, 5),
        )
        val together = staffStepRange(notes)

        notes.forEach { single ->
            val alone = staffStepRange(listOf(single))
            assertTrue(
                "${single.label} needs $alone, which does not fit in $together",
                together.first <= alone.first && together.last >= alone.last,
            )
        }
    }

    @Test
    fun `a note below the staff makes room underneath`() {
        val inside = staffStepRange(listOf(note(NoteName.A, 4)))
        val ledger = staffStepRange(listOf(note(NoteName.C, 4)))

        assertTrue("C4 hangs below the staff on a ledger line", ledger.first < inside.first)
    }

    @Test
    fun `a note above the staff makes room over the top`() {
        val inside = staffStepRange(listOf(note(NoteName.A, 4)))
        val high = staffStepRange(listOf(note(NoteName.C, 6)))

        assertTrue(high.last > inside.last)
    }

    @Test
    fun `the same notes in any order need the same room`() {
        val ascending = listOf(note(NoteName.C, 4), note(NoteName.A, 4), note(NoteName.C, 6))

        assertEquals(staffStepRange(ascending), staffStepRange(ascending.reversed()))
    }

    @Test
    fun `no notes means nothing to make room for`() {
        assertThrows(IllegalArgumentException::class.java) { staffStepRange(emptyList()) }
    }

    @Test
    fun `height follows the range and the line spacing`() {
        val range = staffStepRange(listOf(note(NoteName.A, 4)))

        // Two steps to a staff space, so the drawing is half as many spaces tall as it has steps.
        assertEquals(10.dp * ((range.last - range.first) / 2f), staffHeightOf(range, 10.dp))
        assertEquals(
            staffHeightOf(range, 10.dp) * 2f,
            staffHeightOf(range, 20.dp),
        )
    }

    @Test
    fun `room for a second note is wider than room for one`() {
        assertTrue(
            staffWidthOf(10.dp, reserveSecondaryWidth = true) >
                staffWidthOf(10.dp, reserveSecondaryWidth = false),
        )
    }
}

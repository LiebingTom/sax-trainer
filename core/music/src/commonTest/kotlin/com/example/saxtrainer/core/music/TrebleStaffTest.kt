package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrebleStaffTest {

    private fun steps(name: NoteName, octave: Int) =
        TrebleStaff.placementOf(Note(name, octave)).stepsFromBottomLine

    @Test
    fun `the five staff lines sit on even steps`() {
        assertEquals(0, steps(NoteName.E, 4)) // bottom line
        assertEquals(2, steps(NoteName.G, 4))
        assertEquals(4, steps(NoteName.B, 4)) // middle line
        assertEquals(6, steps(NoteName.D, 5))
        assertEquals(8, steps(NoteName.F, 5)) // top line
    }

    @Test
    fun `the four staff spaces sit on odd steps`() {
        assertEquals(1, steps(NoteName.F, 4))
        assertEquals(3, steps(NoteName.A, 4))
        assertEquals(5, steps(NoteName.C, 5))
        assertEquals(7, steps(NoteName.E, 5))
    }

    @Test
    fun `an octave is seven steps`() {
        assertEquals(steps(NoteName.A, 4) + 7, steps(NoteName.A, 5))
        assertEquals(steps(NoteName.A, 4) - 7, steps(NoteName.A, 3))
        assertEquals(steps(NoteName.C, 4) + 14, steps(NoteName.C, 6))
    }

    @Test
    fun `notes on the staff need no ledger lines`() {
        val a4 = TrebleStaff.placementOf(Note(NoteName.A, 4))

        assertTrue(a4.isOnStaff)
        assertTrue(a4.ledgerLinesBelow.isEmpty())
        assertTrue(a4.ledgerLinesAbove.isEmpty())
    }

    @Test
    fun `notes just outside the staff need no ledger line yet`() {
        // D4 hangs below the bottom line and G5 sits above the top line, both without a ledger.
        val d4 = TrebleStaff.placementOf(Note(NoteName.D, 4))
        val g5 = TrebleStaff.placementOf(Note(NoteName.G, 5))

        assertEquals(-1, d4.stepsFromBottomLine)
        assertEquals(9, g5.stepsFromBottomLine)
        assertTrue(d4.ledgerLinesBelow.isEmpty())
        assertTrue(g5.ledgerLinesAbove.isEmpty())
        assertFalse(d4.isOnStaff)
        assertFalse(g5.isOnStaff)
    }

    @Test
    fun `middle c sits on one ledger line below the staff`() {
        val c4 = TrebleStaff.placementOf(Note(NoteName.C, 4))

        assertEquals(-2, c4.stepsFromBottomLine)
        assertEquals(listOf(-2), c4.ledgerLinesBelow)
        assertTrue(c4.ledgerLinesAbove.isEmpty())
    }

    @Test
    fun `low notes accumulate ledger lines downwards`() {
        // B flat 3 hangs under the first ledger line, A3 sits on the second.
        val bFlat3 = TrebleStaff.placementOf(Note(NoteName.A_SHARP, 3), AccidentalStyle.FLATS)
        assertEquals(-3, bFlat3.stepsFromBottomLine)
        assertEquals(listOf(-2), bFlat3.ledgerLinesBelow)
        assertEquals(listOf(-2, -4), TrebleStaff.placementOf(Note(NoteName.A, 3)).ledgerLinesBelow)
        assertEquals(listOf(-2, -4, -6), TrebleStaff.placementOf(Note(NoteName.F, 3)).ledgerLinesBelow)
    }

    @Test
    fun `high notes accumulate ledger lines upwards`() {
        assertEquals(listOf(10), TrebleStaff.placementOf(Note(NoteName.A, 5)).ledgerLinesAbove)
        assertEquals(listOf(10, 12), TrebleStaff.placementOf(Note(NoteName.C, 6)).ledgerLinesAbove)
        assertEquals(listOf(10, 12, 14), TrebleStaff.placementOf(Note(NoteName.E, 6)).ledgerLinesAbove)
    }

    @Test
    fun `the written tenor sax range is covered`() {
        // Written B flat 3 up to F6: the app never asks for anything outside this.
        val lowest = TrebleStaff.placementOf(Note(NoteName.A_SHARP, 3), AccidentalStyle.FLATS)
        val highest = TrebleStaff.placementOf(Note(NoteName.F, 6))

        assertEquals(-3, lowest.stepsFromBottomLine)
        assertEquals(15, highest.stepsFromBottomLine)
        assertEquals(listOf(10, 12, 14), highest.ledgerLinesAbove)
    }

    @Test
    fun `a sharp shares the line of its natural letter`() {
        val f5 = TrebleStaff.placementOf(Note(NoteName.F, 5))
        val fSharp5 = TrebleStaff.placementOf(Note(NoteName.F_SHARP, 5))

        assertEquals(f5.stepsFromBottomLine, fSharp5.stepsFromBottomLine)
        assertEquals(Accidental.NONE, f5.accidental)
        assertEquals(Accidental.SHARP, fSharp5.accidental)
    }

    @Test
    fun `a flat spelling moves the head to the next letter`() {
        val flat = TrebleStaff.placementOf(Note(NoteName.C_SHARP, 5), AccidentalStyle.FLATS)
        val d5 = TrebleStaff.placementOf(Note(NoteName.D, 5))

        // C sharp 5 spelled as D flat 5 shares D's line.
        assertEquals(d5.stepsFromBottomLine, flat.stepsFromBottomLine)
        assertEquals(Accidental.FLAT, flat.accidental)
    }

    @Test
    fun `a flat spelling stays in the same octave`() {
        val bFlat4 = TrebleStaff.placementOf(Note(NoteName.A_SHARP, 4), AccidentalStyle.FLATS)

        assertEquals(steps(NoteName.B, 4), bFlat4.stepsFromBottomLine)
        assertEquals(Accidental.FLAT, bFlat4.accidental)
    }

    @Test
    fun `naturals are only drawn when asked for`() {
        val implicit = TrebleStaff.placementOf(Note(NoteName.G, 4))
        val explicit = TrebleStaff.placementOf(Note(NoteName.G, 4), naturalsExplicit = true)

        assertEquals(Accidental.NONE, implicit.accidental)
        assertEquals(Accidental.NATURAL, explicit.accidental)
        assertEquals(implicit.stepsFromBottomLine, explicit.stepsFromBottomLine)
    }

    @Test
    fun `an altered note keeps its accidental even when naturals are explicit`() {
        val placement = TrebleStaff.placementOf(Note(NoteName.G_SHARP, 4), naturalsExplicit = true)

        assertEquals(Accidental.SHARP, placement.accidental)
    }

    @Test
    fun `stems point up below the middle line and down from it upwards`() {
        assertTrue(TrebleStaff.placementOf(Note(NoteName.E, 4)).stemUp)
        assertTrue(TrebleStaff.placementOf(Note(NoteName.A, 4)).stemUp)
        assertFalse(TrebleStaff.placementOf(Note(NoteName.B, 4)).stemUp)
        assertFalse(TrebleStaff.placementOf(Note(NoteName.E, 5)).stemUp)
    }

    @Test
    fun `enharmonic spellings describe the same pitch`() {
        val sharp = Note(NoteName.C_SHARP, 5)
        val asSharp = TrebleStaff.placementOf(sharp, AccidentalStyle.SHARPS)
        val asFlat = TrebleStaff.placementOf(sharp, AccidentalStyle.FLATS)

        // Same sounding note, different spelling: one step apart on the staff.
        assertEquals(asSharp.stepsFromBottomLine + 1, asFlat.stepsFromBottomLine)
    }
}

package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which number a note's octave is shown with - and that only the number changes. */
class OctaveNumberingTest {

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `scientific numbering is the octave the note has`() {
        assertEquals("C4", NoteNaming.INTERNATIONAL.labelOf(written("C4")))
        assertEquals("C6", NoteNaming.INTERNATIONAL.labelOf(written("C6"), OctaveNumbering.SCIENTIFIC))
    }

    @Test
    fun `the saxophone count starts at the low C`() {
        fun sax(token: String) = NoteNaming.INTERNATIONAL.labelOf(written(token), OctaveNumbering.SAXOPHONE)

        assertEquals("C1", sax("C4"))
        assertEquals("B1", sax("B4"))
        assertEquals("C2", sax("C5"))
        assertEquals("C3", sax("C6"))
        assertEquals("B♭0", sax("Bb3"))
        assertEquals("B0", sax("B3"))
    }

    @Test
    fun `a written note keeps its letter's octave in either count`() {
        // B sharp 4 sounds as C5, but is written - and so numbered - in the octave of its B.
        assertEquals("B♯1", NoteNaming.INTERNATIONAL.labelOf(written("B#4"), OctaveNumbering.SAXOPHONE))
        assertEquals("C♭2", NoteNaming.INTERNATIONAL.labelOf(written("Cb5"), OctaveNumbering.SAXOPHONE))
    }

    @Test
    fun `the count works with either naming and either way of spelling a pitch`() {
        val aSharp = Note(NoteName.A_SHARP, 4)

        assertEquals("B1", NoteNaming.GERMAN.labelOf(aSharp, octaves = OctaveNumbering.SAXOPHONE))
        assertEquals(
            "A♯1",
            NoteNaming.INTERNATIONAL.labelOf(aSharp, AccidentalStyle.SHARPS, OctaveNumbering.SAXOPHONE),
        )
        assertEquals("H0", NoteNaming.GERMAN.labelOf(Note(NoteName.B, 3), octaves = OctaveNumbering.SAXOPHONE))
    }
}

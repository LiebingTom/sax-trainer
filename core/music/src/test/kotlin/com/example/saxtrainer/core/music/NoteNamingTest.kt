package com.example.saxtrainer.core.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the note letters are called.
 *
 * A display convention: every test here is about spelling and words, and none of them touches pitch,
 * which is the whole claim the setting makes.
 */
class NoteNamingTest {

    private fun note(name: NoteName) = Note(name, 4)

    private fun namesOf(naming: NoteNaming) = NoteName.entries.map { naming.nameOf(note(it)) }

    @Test
    fun `international names the twelve pitches as the setting says`() {
        assertEquals(
            listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "B♭", "B"),
            namesOf(NoteNaming.INTERNATIONAL),
        )
    }

    @Test
    fun `german names the twelve pitches as the setting says`() {
        assertEquals(
            listOf("C", "Cis", "D", "Dis", "E", "F", "Fis", "G", "Gis", "A", "B", "H"),
            namesOf(NoteNaming.GERMAN),
        )
    }

    @Test
    fun `the two systems disagree about exactly one letter`() {
        // B natural is H, and what German calls B is the B flat. Everything else is the same letter.
        val differing = NoteName.entries.filter {
            NoteNaming.INTERNATIONAL.nameOf(note(it)).first() !=
                NoteNaming.GERMAN.nameOf(note(it)).first()
        }

        assertEquals(listOf(NoteName.B), differing)
    }

    @Test
    fun `a label carries the octave`() {
        assertEquals("B♭4", NoteNaming.INTERNATIONAL.labelOf(Note(NoteName.A_SHARP, 4)))
        assertEquals("B4", NoteNaming.GERMAN.labelOf(Note(NoteName.A_SHARP, 4)))
        assertEquals("H3", NoteNaming.GERMAN.labelOf(Note(NoteName.B, 3)))
        assertEquals("Fis5", NoteNaming.GERMAN.labelOf(Note(NoteName.F_SHARP, 5)))
    }

    @Test
    fun `music that has decided how it is written gets its own spelling`() {
        val dSharp = Note(NoteName.D_SHARP, 4)

        assertEquals("D♯4", NoteNaming.INTERNATIONAL.labelOf(dSharp, AccidentalStyle.SHARPS))
        assertEquals("E♭4", NoteNaming.INTERNATIONAL.labelOf(dSharp, AccidentalStyle.FLATS))
        assertEquals("Dis4", NoteNaming.GERMAN.labelOf(dSharp, AccidentalStyle.SHARPS))
        assertEquals("Es4", NoteNaming.GERMAN.labelOf(dSharp, AccidentalStyle.FLATS))
    }

    @Test
    fun `german elides its flats the way the language does`() {
        fun flat(name: NoteName) = NoteNaming.GERMAN.nameOf(note(name), AccidentalStyle.FLATS)

        assertEquals("Des", flat(NoteName.C_SHARP))
        assertEquals("Es", flat(NoteName.D_SHARP))
        assertEquals("Ges", flat(NoteName.F_SHARP))
        assertEquals("As", flat(NoteName.G_SHARP))
        assertEquals("B", flat(NoteName.A_SHARP))
    }

    @Test
    fun `the conventional spelling is sharps, except for the B flat`() {
        NoteName.entries.filter { it != NoteName.A_SHARP }.forEach {
            assertEquals(it.name, AccidentalStyle.SHARPS, note(it).conventionalStyle())
        }
        assertEquals(AccidentalStyle.FLATS, note(NoteName.A_SHARP).conventionalStyle())
    }

    @Test
    fun `a name and a staff position are two views of one spelling`() {
        // Whatever the naming, a note called B flat sits on the B line with a flat in front of it.
        val bFlat = Note(NoteName.A_SHARP, 4)
        val placement = TrebleStaff.placementOf(bFlat, bFlat.conventionalStyle())
        val bNatural = TrebleStaff.placementOf(Note(NoteName.B, 4))

        assertEquals(Accidental.FLAT, placement.accidental)
        assertEquals(bNatural.stepsFromBottomLine, placement.stepsFromBottomLine)
        assertEquals("B♭4", NoteNaming.INTERNATIONAL.labelOf(bFlat))
        assertEquals("B4", NoteNaming.GERMAN.labelOf(bFlat))
    }

    @Test
    fun `naming changes no pitch`() {
        NoteName.entries.forEach {
            val note = note(it)
            assertEquals(note.midiNumber, Note(it, 4).midiNumber)
            assertEquals(
                note.frequencyHz(),
                Note.fromMidi(note.midiNumber).frequencyHz(),
                1e-9,
            )
        }
    }
}

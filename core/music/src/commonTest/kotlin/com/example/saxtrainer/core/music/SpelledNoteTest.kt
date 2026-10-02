package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A written note: its own letter and sign, and the pitch that comes to. */
class SpelledNoteTest {

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `the spellings with no black key behind them sound where they should`() {
        assertEquals(Note(NoteName.B, 3), written("Cb4").note)
        assertEquals(Note(NoteName.E, 4), written("Fb4").note)
        assertEquals(Note(NoteName.F, 4), written("E#4").note)
        assertEquals(Note(NoteName.C, 4), written("B#3").note)
    }

    @Test
    fun `C flat sits on the C line and B sharp on the B line, with their own signs`() {
        val cFlat = TrebleStaff.placementOf(written("Cb5"))
        val c5 = TrebleStaff.placementOf(Note(NoteName.C, 5))
        assertEquals(c5.stepsFromBottomLine, cFlat.stepsFromBottomLine)
        assertEquals(Accidental.FLAT, cFlat.accidental)

        val bSharp = TrebleStaff.placementOf(written("B#4"))
        val b4 = TrebleStaff.placementOf(Note(NoteName.B, 4))
        assertEquals(b4.stepsFromBottomLine, bSharp.stepsFromBottomLine)
        assertEquals(Accidental.SHARP, bSharp.accidental)

        val eSharp = TrebleStaff.placementOf(written("E#4"))
        assertEquals(0, eSharp.stepsFromBottomLine, "E sharp 4 is on the bottom line, where E is")
        assertEquals(Accidental.SHARP, eSharp.accidental)

        val fFlat = TrebleStaff.placementOf(written("Fb4"))
        assertEquals(1, fFlat.stepsFromBottomLine, "F flat 4 is in the first space, where F is")
        assertEquals(Accidental.FLAT, fFlat.accidental)
    }

    @Test
    fun `two spellings of one pitch are one pitch and two written notes`() {
        val dSharp = written("D#4")
        val eFlat = written("Eb4")

        assertEquals(dSharp.note, eFlat.note)
        assertNotEquals(dSharp, eFlat)
        assertNotEquals(
            TrebleStaff.placementOf(dSharp).stepsFromBottomLine,
            TrebleStaff.placementOf(eFlat).stepsFromBottomLine,
        )
    }

    @Test
    fun `every flat and every sharp can be written`() {
        val flats = listOf("Cb4", "Db4", "Eb4", "Fb4", "Gb4", "Ab4", "Bb4")
        val sharps = listOf("C#4", "D#4", "E#4", "F#4", "G#4", "A#4", "B#4")

        (flats + sharps).forEach { token ->
            val note = written(token)
            assertEquals(token, note.token, "read and written back")
            assertTrue(note.accidental != Accidental.NONE, token)
        }
    }

    @Test
    fun `a token is read with ascii or music signs, and anything else is not a note`() {
        assertEquals(written("Eb4"), SpelledNote.parse("E♭4"))
        assertEquals(written("F#4"), SpelledNote.parse("F♯4"))
        assertNull(SpelledNote.parse("H4"))
        assertNull(SpelledNote.parse("Ebb4"))
        assertNull(SpelledNote.parse(""))
    }

    @Test
    fun `a natural sign is a way of showing a note, not a note of its own`() {
        assertFailsWith<IllegalArgumentException> { SpelledNote(0, Accidental.NATURAL, 4) }
        assertEquals(
            Accidental.NATURAL,
            TrebleStaff.placementOf(written("C4"), naturalsExplicit = true).accidental,
        )
    }

    @Test
    fun `each pitch can be written its usual way and its other single-sign ways`() {
        assertEquals(listOf("C#4", "Db4"), SpelledNote.spellingsOf(Note(NoteName.C_SHARP, 4)).map { it.token })
        assertEquals(listOf("Bb4", "A#4"), SpelledNote.spellingsOf(Note(NoteName.A_SHARP, 4)).map { it.token })
        assertEquals(listOf("E4", "Fb4"), SpelledNote.spellingsOf(Note(NoteName.E, 4)).map { it.token })
        assertEquals(listOf("C4", "B#3"), SpelledNote.spellingsOf(Note(NoteName.C, 4)).map { it.token })
        assertEquals(listOf("D4"), SpelledNote.spellingsOf(Note(NoteName.D, 4)).map { it.token })

        NoteName.entries.forEach { name ->
            val pitch = Note(name, 4)
            assertTrue(SpelledNote.spellingsOf(pitch).all { it.note == pitch }, "$name")
        }
    }

    @Test
    fun `a heard pitch borrows a spelling only from a note that sounds the same`() {
        val exercise = listOf(written("C4"), written("Eb4"), written("Ab4"))

        assertEquals(written("Eb4"), Note(NoteName.D_SHARP, 4).spelledAs(exercise))
        assertEquals(written("F#4"), Note(NoteName.F_SHARP, 4).spelledAs(exercise), "the usual spelling")
        assertEquals(
            written("Gb4"),
            Note(NoteName.F_SHARP, 4).spelledAs(exercise, AccidentalStyle.FLATS),
            "or the one the music asks for",
        )
        assertEquals(written("D#4"), Note(NoteName.D_SHARP, 4).spelledAs(listOf(written("D#4"), written("Eb4"))), "first one wins")
    }

    @Test
    fun `pitch order reads up the staff`() {
        val sorted = listOf("Eb4", "C4", "D#4", "B#3", "E4").map(::written).sortedWith(SpelledNote.PITCH_ORDER)

        assertEquals(listOf("B#3", "C4", "D#4", "Eb4", "E4"), sorted.map { it.token })
    }

    // --- in a sequence --------------------------------------------------------------------

    @Test
    fun `a step's own spelling wins over the sequence's style`() {
        val sequence = ToneSequence(
            steps = listOf(
                ToneStep.of(written("Eb4"), Beats.of(1)),
                ToneStep(Note(NoteName.D_SHARP, 4), Beats.of(1)),
                ToneStep.rest(Beats.of(1)),
            ),
            accidentalStyle = AccidentalStyle.SHARPS,
        )

        assertEquals(written("Eb4"), sequence.writtenAt(0))
        assertEquals(written("D#4"), sequence.writtenAt(1), "no spelling of its own: the style decides")
        assertNull(sequence.writtenAt(2))
        assertEquals(listOf(written("Eb4"), written("D#4")), sequence.writtenNotes)
        assertEquals(
            listOf(written("Eb4"), written("D#4"), null),
            sequence.spelledSteps().map { it.written },
            "taken out of the sequence, every note keeps how it was written",
        )
    }

    @Test
    fun `a step cannot be written as a note of another pitch`() {
        assertFailsWith<IllegalArgumentException> {
            ToneStep(Note(NoteName.D, 4), Beats.of(1), written = written("Eb4"))
        }
    }
}

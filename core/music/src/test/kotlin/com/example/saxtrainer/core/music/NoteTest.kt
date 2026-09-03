package com.example.saxtrainer.core.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {

    @Test
    fun `a4 is midi 69 and 440 hertz`() {
        val a4 = Note(NoteName.A, 4)
        assertEquals(69, a4.midiNumber)
        assertEquals(440.0, a4.frequencyHz(), 1e-9)
    }

    @Test
    fun `an octave down halves the frequency`() {
        assertEquals(220.0, Note(NoteName.A, 3).frequencyHz(), 1e-9)
        assertEquals(880.0, Note(NoteName.A, 5).frequencyHz(), 1e-9)
    }

    @Test
    fun `middle c is midi 60`() {
        assertEquals(60, Note(NoteName.C, 4).midiNumber)
        assertEquals(Note(NoteName.C, 4), Note.fromMidi(60))
    }

    @Test
    fun `tuning reference shifts every pitch`() {
        assertEquals(442.0, Note(NoteName.A, 4).frequencyHz(referenceA4Hz = 442.0), 1e-9)
    }

    @Test
    fun `nearest note snaps a measured frequency`() {
        assertEquals(Note(NoteName.A, 4), Note.nearestTo(441.0))
        assertEquals(Note(NoteName.C, 4), Note.nearestTo(262.0))
    }

    @Test
    fun `range is chromatic and inclusive`() {
        val range = Note.range(Note(NoteName.C, 4), Note(NoteName.C, 5))
        assertEquals(13, range.size)
        assertEquals(Note(NoteName.C, 4), range.first())
        assertEquals(Note(NoteName.C, 5), range.last())
    }

    @Test
    fun `a soprano saxophone sounds a tone below the written note`() {
        val written = Note(NoteName.C, 4)
        val sounding = Note(NoteName.A_SHARP, 3)

        assertEquals(sounding, Transposition.SOPRANO_B_FLAT.concertFor(written))
        assertEquals(written, Transposition.SOPRANO_B_FLAT.writtenFor(sounding))
    }

    @Test
    fun `an alto saxophone sounds a major sixth below the written note`() {
        val written = Note(NoteName.C, 5)
        val sounding = Note(NoteName.D_SHARP, 4)

        assertEquals(sounding, Transposition.ALTO_E_FLAT.concertFor(written))
        assertEquals(written, Transposition.ALTO_E_FLAT.writtenFor(sounding))
    }

    @Test
    fun `a tenor saxophone sounds an octave and a tone below the written note`() {
        val written = Note(NoteName.C, 4)
        val sounding = Note(NoteName.A_SHARP, 2)

        assertEquals(sounding, Transposition.TENOR_B_FLAT.concertFor(written))
        assertEquals(written, Transposition.TENOR_B_FLAT.writtenFor(sounding))
    }

    @Test
    fun `a baritone saxophone sounds an octave and a major sixth below the written note`() {
        val written = Note(NoteName.C, 5)
        val sounding = Note(NoteName.D_SHARP, 3)

        assertEquals(sounding, Transposition.BARITONE_E_FLAT.concertFor(written))
        assertEquals(written, Transposition.BARITONE_E_FLAT.writtenFor(sounding))
    }

    @Test
    fun `the same key in different sizes transposes an octave apart`() {
        // The key alone does not fix the distance: a tenor is an octave below a soprano, and a
        // baritone an octave below an alto. Getting this wrong makes pitch checking fail.
        assertEquals(
            12,
            Transposition.TENOR_B_FLAT.semitonesAboveConcert -
                Transposition.SOPRANO_B_FLAT.semitonesAboveConcert,
        )
        assertEquals(
            12,
            Transposition.BARITONE_E_FLAT.semitonesAboveConcert -
                Transposition.ALTO_E_FLAT.semitonesAboveConcert,
        )
    }

    @Test
    fun `every transposition round trips`() {
        val written = Note(NoteName.G, 4)
        Transposition.entries.forEach { transposition ->
            assertEquals(
                transposition.displayName,
                written,
                transposition.writtenFor(transposition.concertFor(written)),
            )
        }
    }

    @Test
    fun `concert transposition changes nothing`() {
        val note = Note(NoteName.G, 4)
        assertEquals(note, Transposition.CONCERT.writtenFor(note))
        assertEquals(note, Transposition.CONCERT.concertFor(note))
    }
}

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
    fun `written pitch for a b flat instrument is a tone above concert`() {
        val concertBFlat = Note(NoteName.A_SHARP, 3)
        assertEquals(Note(NoteName.C, 4), Transposition.B_FLAT.writtenFor(concertBFlat))
        assertEquals(concertBFlat, Transposition.B_FLAT.concertFor(Note(NoteName.C, 4)))
    }

    @Test
    fun `written pitch for an e flat instrument is a major sixth above concert`() {
        val concertEFlat = Note(NoteName.D_SHARP, 4)
        assertEquals(Note(NoteName.C, 5), Transposition.E_FLAT.writtenFor(concertEFlat))
        assertEquals(concertEFlat, Transposition.E_FLAT.concertFor(Note(NoteName.C, 5)))
    }

    @Test
    fun `concert transposition changes nothing`() {
        val note = Note(NoteName.G, 4)
        assertEquals(note, Transposition.CONCERT.writtenFor(note))
        assertEquals(note, Transposition.CONCERT.concertFor(note))
    }
}

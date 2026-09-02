package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaxophoneFingeringsTest {

    private val chart = SaxophoneFingerings

    private fun note(name: NoteName, octave: Int) = Note(name, octave)

    @Test
    fun `the first octave a beginner learns is covered`() {
        val expected = listOf(
            note(NoteName.C, 4),
            note(NoteName.D, 4),
            note(NoteName.E, 4),
            note(NoteName.F, 4),
            note(NoteName.F_SHARP, 4),
            note(NoteName.G, 4),
            note(NoteName.A, 4),
            note(NoteName.A_SHARP, 4),
            note(NoteName.B, 4),
            note(NoteName.C, 5),
        )

        assertEquals(expected, chart.supportedNotes)
    }

    @Test
    fun `every supported note resolves to a fingering`() {
        chart.supportedNotes.forEach { note ->
            assertTrue(note.label, chart.supports(note))
            assertTrue(note.label, chart.fingeringFor(note)!!.pressedKeys.isNotEmpty())
        }
    }

    @Test
    fun `supported notes are sorted and free of duplicates`() {
        val notes = chart.supportedNotes

        assertEquals(notes.sortedBy { it.midiNumber }, notes)
        assertEquals(notes.size, notes.distinct().size)
    }

    @Test
    fun `notes outside the chart have no fingering`() {
        assertNull(chart.fingeringFor(note(NoteName.D, 5)))
        assertNull(chart.fingeringFor(note(NoteName.G, 3)))
        assertFalse(chart.supports(note(NoteName.C, 6)))
    }

    @Test
    fun `low c uses the whole hand and the little finger`() {
        val fingering = chart.fingeringFor(note(NoteName.C, 4))!!

        assertEquals(
            setOf(
                SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
                SaxKey.RH1, SaxKey.RH2, SaxKey.RH3,
                SaxKey.LOW_C,
            ),
            fingering.pressedKeys,
        )
        assertTrue(fingering.isPressed(SaxKey.LOW_C))
    }

    @Test
    fun `fingers lift one at a time from d to g`() {
        val d = chart.fingeringFor(note(NoteName.D, 4))!!.pressedKeys
        val e = chart.fingeringFor(note(NoteName.E, 4))!!.pressedKeys
        val f = chart.fingeringFor(note(NoteName.F, 4))!!.pressedKeys
        val g = chart.fingeringFor(note(NoteName.G, 4))!!.pressedKeys

        assertEquals(6, d.size)
        assertEquals(5, e.size)
        assertEquals(4, f.size)
        assertEquals(3, g.size)
        // Each higher note keeps the keys of the next one down.
        assertTrue(e.containsAll(f))
        assertTrue(f.containsAll(g))
        assertTrue(d.containsAll(e))
    }

    @Test
    fun `the left hand alone plays g a b and c`() {
        assertEquals(
            setOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3),
            chart.fingeringFor(note(NoteName.G, 4))!!.pressedKeys,
        )
        assertEquals(
            setOf(SaxKey.LH1, SaxKey.LH2),
            chart.fingeringFor(note(NoteName.A, 4))!!.pressedKeys,
        )
        assertEquals(setOf(SaxKey.LH1), chart.fingeringFor(note(NoteName.B, 4))!!.pressedKeys)
        assertEquals(setOf(SaxKey.LH2), chart.fingeringFor(note(NoteName.C, 5))!!.pressedKeys)
    }

    @Test
    fun `b flat uses the side key`() {
        val bFlat = chart.fingeringFor(note(NoteName.A_SHARP, 4))!!

        assertTrue(bFlat.isPressed(SaxKey.SIDE_BB))
        assertTrue(bFlat.isPressed(SaxKey.LH1))
        assertEquals(2, bFlat.pressedKeys.size)
    }

    @Test
    fun `f sharp differs from f by one finger`() {
        val f = chart.fingeringFor(note(NoteName.F, 4))!!.pressedKeys
        val fSharp = chart.fingeringFor(note(NoteName.F_SHARP, 4))!!.pressedKeys

        assertEquals(f.size, fSharp.size)
        assertTrue(fSharp.contains(SaxKey.RH2))
        assertFalse(fSharp.contains(SaxKey.RH1))
    }

    @Test
    fun `no note in this range needs the octave key`() {
        // The octave register starts above this chart, so the thumb key stays open throughout.
        chart.supportedNotes.forEach { note ->
            assertFalse(note.label, chart.fingeringFor(note)!!.isPressed(SaxKey.OCTAVE))
        }
    }

    @Test
    fun `the same written note gives the same fingering whatever the instrument sounds`() {
        // Charts are keyed by written pitch, which is what makes one chart serve tenor and alto.
        val written = note(NoteName.G, 4)

        assertEquals(chart.fingeringFor(written), chart.fingeringFor(written))
        assertEquals(
            setOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3),
            chart.fingeringFor(written)!!.pressedKeys,
        )
    }
}

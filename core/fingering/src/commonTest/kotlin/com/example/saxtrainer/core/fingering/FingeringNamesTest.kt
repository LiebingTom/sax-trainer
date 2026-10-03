package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What each fingering of a note is called, as the lexicon captions it. */
class FingeringNamesTest {

    private fun captionsOf(name: NoteName, octave: Int): List<String> {
        val fingerings = SaxophoneFingerings.fingeringsFor(Note(name, octave))!!
        return fingerings.all.map(fingerings::captionOf)
    }

    @Test
    fun `B flat's grips are the bis, the side key and one and one`() {
        assertEquals(listOf("Standard", "Side B♭", "R1"), captionsOf(NoteName.A_SHARP, 4))
    }

    @Test
    fun `a side key names the grip, not the finger that moves with it`() {
        assertEquals(listOf("Standard", "Side F♯"), captionsOf(NoteName.F_SHARP, 4))
    }

    @Test
    fun `the front F names the high alternates`() {
        assertEquals(listOf("Standard", "Front F"), captionsOf(NoteName.E, 6))
        assertEquals(listOf("Standard", "Side B♭ + Front F"), captionsOf(NoteName.F_SHARP, 6))
    }

    @Test
    fun `a note with one grip has only the standard one`() {
        assertEquals(listOf("Standard"), captionsOf(NoteName.G, 4))
    }

    @Test
    fun `every alternate in the chart has a name of its own`() {
        SaxophoneFingerings.supportedNotes.forEach { note ->
            val fingerings = SaxophoneFingerings.fingeringsFor(note)!!
            val captions = fingerings.all.map(fingerings::captionOf)
            assertEquals(captions.size, captions.distinct().size, "$note: $captions")
            assertTrue(captions.none { it.isBlank() }, "$note: $captions")
        }
    }

    @Test
    fun `key labels read as a player writes them`() {
        assertEquals("Side B♭", SaxKey.SIDE_BB.displayName)
        assertEquals("Side F♯", SaxKey.SIDE_F_SHARP.displayName)
        assertEquals("Bis", SaxKey.BIS.displayName)
        assertEquals("RH E♭", SaxKey.RH_EB.displayName)
        assertEquals("L3", SaxKey.LH3.displayName)
    }
}

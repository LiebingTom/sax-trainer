package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A key's notes, written the way the key writes them. */
class KeyTest {

    private val c4 = Note(NoteName.C, 4)
    private val c5 = Note(NoteName.C, 5)
    private val d6 = Note(NoteName.D, 6)

    private fun key(name: String) = Key.all.single { it.nameIn(NoteNaming.INTERNATIONAL) == name }

    private fun tokens(key: Key, low: Note, high: Note) = key.notesBetween(low, high).map { it.token }

    @Test
    fun `C major is the white keys`() {
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            tokens(key("C major"), c4, c5),
        )
    }

    @Test
    fun `F major has a B flat, never an A sharp`() {
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "Bb4", "C5"),
            tokens(key("F major"), c4, c5),
        )
    }

    @Test
    fun `D major has F sharp and C sharp in every octave of the range`() {
        val tokens = tokens(key("D major"), c4, d6)

        listOf("C#4", "F#4", "C#5", "F#5", "C#6", "D6").forEach {
            assertTrue(it in tokens, "$it missing: $tokens")
        }
        assertTrue(tokens.none { it == "C4" || it == "F4" }, "no plain C or F: $tokens")
    }

    @Test
    fun `E flat major is written with flats only`() {
        val tokens = tokens(key("E♭ major"), c4, d6)

        assertTrue(tokens.none { '#' in it }, "no sharps: $tokens")
        listOf("Eb4", "Ab4", "Bb4").forEach { assertTrue(it in tokens, "$it missing: $tokens") }
    }

    @Test
    fun `the far keys use the rare spellings their letters need`() {
        assertTrue("E#4" in tokens(key("F♯ major"), c4, c5))
        assertTrue("Cb5" in tokens(key("G♭ major"), c4, d6))
    }

    @Test
    fun `a minor key is spelled from its own root`() {
        assertEquals(
            listOf("D4", "E4", "F4", "G4", "A4", "Bb4", "C5"),
            tokens(key("D minor"), Note(NoteName.D, 4), c5),
        )
    }

    @Test
    fun `every key has seven notes to the octave and a German name`() {
        Key.all.forEach { key ->
            val octave = key.notesBetween(c4, Note(NoteName.B, 4))
            assertEquals(7, octave.size, key.nameIn(NoteNaming.INTERNATIONAL))
            assertEquals(7, octave.map { it.letterIndex }.distinct().size, "one note per letter")
        }
        assertEquals("B major", key("B♭ major").nameIn(NoteNaming.GERMAN))
        assertEquals(26, Key.all.size)
    }
}

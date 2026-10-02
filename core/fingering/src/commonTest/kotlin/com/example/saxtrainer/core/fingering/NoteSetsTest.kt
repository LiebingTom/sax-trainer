package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteSetsTest {

    private val chart = TenorSaxophoneFingerings

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `the chromatic set is every note the chart can show, once each, in pitch order`() {
        val notes = NoteSets.notesOf(NoteSet.CHROMATIC, chart, customNotes = emptySet())

        assertEquals(chart.supportedNotes.sortedBy { it.midiNumber }, notes.map { it.note })
    }

    @Test
    fun `the chromatic set holds all twelve pitch classes`() {
        val names = NoteSets.notesOf(NoteSet.CHROMATIC, chart, customNotes = emptySet())
            .map { it.note.name }
            .toSet()

        assertEquals(NoteName.entries.toSet(), names)
    }

    @Test
    fun `the chromatic set ignores the custom choice`() {
        val custom = setOf(written("C4"))

        assertEquals(
            NoteSets.notesOf(NoteSet.CHROMATIC, chart, emptySet()),
            NoteSets.notesOf(NoteSet.CHROMATIC, chart, custom),
        )
    }

    @Test
    fun `the ready-made sets are written the way their notes usually are`() {
        val plus = NoteSets.notesOf(NoteSet.BEGINNER_PLUS, chart, emptySet()).map { it.token }

        assertTrue("Bb4" in plus && "F#4" in plus, "B flat and F sharp, as the set says: $plus")
    }

    @Test
    fun `a custom set keeps its spellings, both of one pitch included`() {
        val custom = setOf(written("Eb4"), written("D#4"), written("Cb5"), written("C4"))

        val notes = NoteSets.notesOf(NoteSet.CUSTOM, chart, custom)

        assertEquals(listOf("C4", "D#4", "Eb4", "Cb5"), notes.map { it.token })
    }

    @Test
    fun `a custom note the chart cannot finger is dropped, however it is written`() {
        val custom = setOf(written("C4"), written("Cb2"))

        assertEquals(listOf(written("C4")), NoteSets.notesOf(NoteSet.CUSTOM, chart, custom))
    }

    @Test
    fun `the choices offer every pitch in every single-sign spelling`() {
        val choices = NoteSets.choicesOf(chart)

        assertEquals(chart.supportedNotes.toSet(), choices.map { it.note }.toSet())
        listOf("D#4", "Eb4", "E#4", "Fb4", "B#4", "Cb5", "Gb4", "Ab4").forEach {
            assertTrue(written(it) in choices, "$it is offered")
        }
        assertEquals(choices.size, choices.distinct().size, "no spelling twice")
        assertTrue(choices.none { it.note == Note(NoteName.C, 2) })
    }
}

package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceCatalogTest {

    private val root = SequenceCatalog.defaultRoot

    private fun note(name: NoteName, octave: Int) = Note(name, octave)

    private fun notesOf(pattern: ExercisePattern) = pattern.notesFrom(root)

    @Test
    fun `the default root is concert a4`() {
        assertEquals(note(NoteName.A, 4), root)
        assertEquals(440.0, root.frequencyHz(), 1e-9)
    }

    @Test
    fun `single note is one note`() {
        assertEquals(listOf(note(NoteName.A, 4)), notesOf(SequenceCatalog.singleNote))
        assertEquals(listOf(0), SequenceCatalog.singleNote.offsets)
        assertEquals(5.0, SequenceCatalog.singleNote.defaultBeatsPerNote.value, 1e-9)
    }

    @Test
    fun `repeat is ten identical notes and not a special kind of pattern`() {
        val notes = notesOf(SequenceCatalog.repeat)

        assertEquals(10, notes.size)
        assertTrue(notes.all { it == note(NoteName.A, 4) })
        assertEquals(List(10) { 0 }, SequenceCatalog.repeat.offsets)
    }

    @Test
    fun `neighboring notes alternate`() {
        assertEquals(listOf(0, 2, 0, 2, 0), SequenceCatalog.neighboringNotes.offsets)
        assertEquals(
            listOf(
                note(NoteName.A, 4),
                note(NoteName.B, 4),
                note(NoteName.A, 4),
                note(NoteName.B, 4),
                note(NoteName.A, 4),
            ),
            notesOf(SequenceCatalog.neighboringNotes),
        )
    }

    @Test
    fun `stepwise up rises four notes`() {
        assertEquals(listOf(0, 2, 3, 5), SequenceCatalog.stepwiseUp.offsets)
        assertEquals(
            listOf(
                note(NoteName.A, 4),
                note(NoteName.B, 4),
                note(NoteName.C, 5),
                note(NoteName.D, 5),
            ),
            notesOf(SequenceCatalog.stepwiseUp),
        )
    }

    @Test
    fun `stepwise up and down returns to the root`() {
        assertEquals(listOf(0, 2, 3, 5, 3, 2, 0), SequenceCatalog.stepwiseUpAndDown.offsets)
        assertEquals(
            listOf(
                note(NoteName.A, 4),
                note(NoteName.B, 4),
                note(NoteName.C, 5),
                note(NoteName.D, 5),
                note(NoteName.C, 5),
                note(NoteName.B, 4),
                note(NoteName.A, 4),
            ),
            notesOf(SequenceCatalog.stepwiseUpAndDown),
        )
    }

    @Test
    fun `ascending thirds climb in pairs`() {
        assertEquals(listOf(0, 3, 2, 5, 3, 7), SequenceCatalog.ascendingThirds.offsets)
        assertEquals(
            listOf(
                note(NoteName.A, 4),
                note(NoteName.C, 5),
                note(NoteName.B, 4),
                note(NoteName.D, 5),
                note(NoteName.C, 5),
                note(NoteName.E, 5),
            ),
            notesOf(SequenceCatalog.ascendingThirds),
        )
    }

    @Test
    fun `the fifth alternates over seven semitones`() {
        assertEquals(listOf(0, 7, 0, 7), SequenceCatalog.fifth.offsets)
        assertEquals(
            listOf(
                note(NoteName.A, 4),
                note(NoteName.E, 5),
                note(NoteName.A, 4),
                note(NoteName.E, 5),
            ),
            notesOf(SequenceCatalog.fifth),
        )
    }

    @Test
    fun `every pattern except the single note holds four beats per note`() {
        SequenceCatalog.patterns
            .filter { it != SequenceCatalog.singleNote }
            .forEach { assertEquals(it.name, 4.0, it.defaultBeatsPerNote.value, 1e-9) }
    }

    @Test
    fun `transposing the root shifts every note by the same interval`() {
        val fromC4 = SequenceCatalog.ascendingThirds.notesFrom(note(NoteName.C, 4))

        assertEquals(
            listOf(
                note(NoteName.C, 4),
                note(NoteName.D_SHARP, 4),
                note(NoteName.D, 4),
                note(NoteName.F, 4),
                note(NoteName.D_SHARP, 4),
                note(NoteName.G, 4),
            ),
            fromC4,
        )
        // Same shape, only moved: every note is nine semitones below the A4 version.
        notesOf(SequenceCatalog.ascendingThirds).zip(fromC4).forEach { (high, low) ->
            assertEquals(9, high.midiNumber - low.midiNumber)
        }
    }

    @Test
    fun `building a sequence keeps the pattern identity and length`() {
        val sequence = SequenceCatalog.stepwiseUp.build(root)

        assertEquals("Stepwise Up", sequence.name)
        assertEquals(SequenceCatalog.stepwiseUp.description, sequence.description)
        assertEquals(notesOf(SequenceCatalog.stepwiseUp), sequence.notes)
        assertEquals(16.0, sequence.totalBeats.value, 1e-9)
    }

    @Test
    fun `the note length can be overridden per exercise`() {
        val sequence = SequenceCatalog.repeat.build(root, beatsPerNote = Beats.of(2))

        assertEquals(20.0, sequence.totalBeats.value, 1e-9)
        assertTrue(sequence.steps.all { it.duration == Beats.of(2) })
    }

    @Test
    fun `patterns are addressable by id`() {
        SequenceCatalog.patterns.forEach { pattern ->
            assertEquals(pattern, SequenceCatalog.byId(pattern.id))
        }
        assertNull(SequenceCatalog.byId("nope"))
        assertEquals(7, SequenceCatalog.patterns.size)
        assertEquals(
            SequenceCatalog.patterns.size,
            SequenceCatalog.patterns.map { it.id }.distinct().size,
        )
    }
}

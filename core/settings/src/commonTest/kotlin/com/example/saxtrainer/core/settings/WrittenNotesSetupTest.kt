package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.SpelledNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A choice of written notes, written down and read back with its spellings intact. */
class WrittenNotesSetupTest {

    private fun written(vararg tokens: String) = tokens.map { SpelledNote.parse(it)!! }

    private fun setupOf(notes: List<SpelledNote>) = exerciseSetup {
        putWrittenNotes(PITCHES, WRITTEN, notes)
    }

    @Test
    fun `spellings survive being written down and read back`() {
        val notes = written("C4", "D#4", "Eb4", "E#4", "Fb4", "B#3", "Cb5", "Ab4", "Bb4")

        val read = setupOf(notes).writtenNotes(PITCHES, WRITTEN)

        assertEquals(notes.sortedWith(SpelledNote.PITCH_ORDER), read)
    }

    @Test
    fun `the pitches are still there for whatever only reads pitches`() {
        val setup = setupOf(written("D#4", "Eb4", "C4"))

        assertEquals(listOf(60, 63), setup.ints(PITCHES), "one number for a pitch chosen twice")
    }

    @Test
    fun `a setup with only pitches is read with the usual spellings`() {
        val old = exerciseSetup { putInts(PITCHES, listOf(60, 61, 63, 66, 68, 70)) }

        assertEquals(
            listOf("C4", "C#4", "D#4", "F#4", "G#4", "Bb4"),
            old.writtenNotes(PITCHES, WRITTEN)?.map { it.token },
        )
    }

    @Test
    fun `nothing stored is not the same as nothing chosen`() {
        assertNull(ExerciseSetup.EMPTY.writtenNotes(PITCHES, WRITTEN))
        assertEquals(emptyList(), setupOf(emptyList()).writtenNotes(PITCHES, WRITTEN))
    }

    @Test
    fun `a token this version cannot read is dropped, not fatal`() {
        val setup = exerciseSetup {
            putInts(PITCHES, listOf(60, 63))
            put(WRITTEN, "C4,Eb4,Q9")
        }

        assertEquals(written("C4", "Eb4"), setup.writtenNotes(PITCHES, WRITTEN))
    }

    @Test
    fun `spellings survive a saved lesson's own storage`() {
        val setup = setupOf(written("C4", "D4", "Eb4", "F4", "G4", "Ab4", "Bb4"))
        val book = UserLessonBook().plusLesson("Flats", UserLessonBook.DEFAULT_GROUP.id, "NOTE_TRAINER", setup)

        val read = StoredUserLessons.decode(StoredUserLessons.encode(book)).lessons.single().setup

        assertEquals(
            listOf("C4", "D4", "Eb4", "F4", "G4", "Ab4", "Bb4"),
            read.writtenNotes(PITCHES, WRITTEN)?.map { it.token },
        )
    }

    @Test
    fun `a list of words reads back as written, and a comma cannot be smuggled into one`() {
        val setup = exerciseSetup { putStrings("words", listOf("Eb4", "C#5")) }

        assertEquals(listOf("Eb4", "C#5"), setup.strings("words"))
        assertNull(ExerciseSetup.EMPTY.strings("words"))
        assertFailsWith<IllegalArgumentException> { exerciseSetup { putStrings("words", listOf("a,b")) } }
    }

    private companion object {
        const val PITCHES = "custom_notes"
        const val WRITTEN = "custom_written_notes"
    }
}

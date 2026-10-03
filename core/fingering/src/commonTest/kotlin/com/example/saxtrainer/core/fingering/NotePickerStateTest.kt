package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Key
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.SpelledNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The note picker's rules, decided without a screen. */
class NotePickerStateTest {

    private val chart = TenorSaxophoneFingerings

    private fun written(token: String) = SpelledNote.parse(token)!!

    private fun picker(chosen: Set<SpelledNote> = emptySet()) =
        NotePickerState.open(NoteSets.choicesOf(chart), chosen)

    private fun key(name: String) = Key.all.single { it.nameIn(NoteNaming.INTERNATIONAL) == name }

    @Test
    fun `the registers turn where the octave key and the palm keys come in`() {
        assertEquals(Register.LOW, Register.of(Note(NoteName.C_SHARP, 5)))
        assertEquals(Register.MIDDLE, Register.of(Note(NoteName.D, 5)))
        assertEquals(Register.MIDDLE, Register.of(Note(NoteName.C_SHARP, 6)))
        assertEquals(Register.PALM, Register.of(Note(NoteName.D, 6)))
        assertEquals(Register.LOW, Register.of(Note(NoteName.A_SHARP, 3)))
    }

    @Test
    fun `the rare spellings are the four white keys on a neighbouring letter`() {
        listOf("E#4", "B#4", "Cb5", "Fb4").forEach {
            assertEquals(SpellingKind.RARE, SpellingKind.of(written(it)), it)
        }
        assertEquals(SpellingKind.SHARP, SpellingKind.of(written("F#4")))
        assertEquals(SpellingKind.FLAT, SpellingKind.of(written("Bb4")))
        assertEquals(SpellingKind.NATURAL, SpellingKind.of(written("E4")))
    }

    @Test
    fun `a fresh window hides only the rare spellings`() {
        val state = picker()

        assertTrue(state.shown.none { SpellingKind.of(it) == SpellingKind.RARE })
        assertTrue(written("Eb4") in state.shown && written("D#4") in state.shown)
        assertFalse(written("E#4") in state.shown)
        val rare = state.choices.count { SpellingKind.of(it) == SpellingKind.RARE }
        assertEquals(state.choices.size - rare, state.shown.size)
    }

    @Test
    fun `all shown and none shown leave hidden notes as they are`() {
        val rare = written("E#4")
        val state = picker(chosen = setOf(rare)).toggleRegister(Register.PALM)

        val all = state.selectAllShown()
        assertTrue(rare in all.chosen, "hidden, still chosen")
        assertTrue(
            all.chosen.none { Register.of(it.note) == Register.PALM },
            "palm keys hidden, so not added",
        )
        assertEquals(1, all.hiddenChosenCount)

        val none = all.clearShown()
        assertEquals(setOf(rare), none.chosen)
    }

    @Test
    fun `toggling a note adds it and takes it away again`() {
        val note = written("G4")
        val state = picker().toggle(note)

        assertEquals(setOf(note), state.chosen)
        assertTrue(state.canApply)
        assertFalse(state.toggle(note).canApply)
    }

    @Test
    fun `using a key fills its notes in the registers shown`() {
        val state = picker(chosen = setOf(written("G#4")))
            .toggleRegister(Register.PALM)
            .useKey(key("B♭ major"))

        val tokens = state.chosen.map { it.token }.toSet()
        assertTrue(setOf("Bb4", "Eb4", "Bb5", "Eb5").all { it in tokens }, "$tokens")
        assertTrue(tokens.none { '#' in it }, "B flat major has no sharps: $tokens")
        assertTrue(state.chosen.none { Register.of(it.note) == Register.PALM }, "palm keys off")
        assertTrue(written("Bb3") in state.chosen, "down to the lowest note the horn has")
        assertEquals(key("B♭ major"), state.key)
        assertFalse("G#4" in tokens, "the draft was replaced")
    }

    @Test
    fun `a key that needs a rare spelling shows it`() {
        val state = picker().useKey(key("F♯ major"))

        assertTrue(written("E#4") in state.chosen)
        assertEquals(0, state.hiddenChosenCount)
    }

    @Test
    fun `using notes replaces the draft and shows every one of them`() {
        val notes = listOf("Bb4", "E#4", "D6", "C9").map(::written)
        val state = picker(chosen = setOf(written("G4")))
            .toggleRegister(Register.PALM)
            .useNotes(notes)

        assertEquals(setOf("Bb4", "E#4", "D6"), state.chosen.map { it.token }.toSet(), "C9 is no note here")
        assertEquals(0, state.hiddenChosenCount, "palm keys and rare spellings switched back on")
    }

    @Test
    fun `starting from a set replaces the draft, and the result is recognised as that set`() {
        val state = picker(chosen = setOf(written("D#4"))).startFrom(NoteSet.BEGINNER_PLUS, chart)

        assertEquals(NoteSet.BEGINNER_PLUS, NoteSets.matchingSet(state.chosen, chart))
        val edited = state.toggle(written("D#4"))
        assertEquals(NoteSet.CUSTOM, NoteSets.matchingSet(edited.chosen, chart))
        assertEquals(
            NoteSet.CHROMATIC,
            NoteSets.matchingSet(state.startFrom(NoteSet.CHROMATIC, chart).chosen, chart),
        )
    }
}

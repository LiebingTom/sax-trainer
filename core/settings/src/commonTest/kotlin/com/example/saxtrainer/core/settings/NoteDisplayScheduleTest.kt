package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.random.Random
import kotlin.test.assertNull

class NoteDisplayScheduleTest {

    @Test
    fun `a fixed presentation is used for every note`() {
        listOf(NoteDisplay.NAME, NoteDisplay.STAFF).forEach { display ->
            assertEquals(List(4) { display }, noteDisplaySchedule(display, stepCount = 4))
        }
    }

    @Test
    fun `a fixed presentation draws no randomness`() {
        // The same generator must be left where it was, so switching the option on is the only
        // thing that changes any other draw in an exercise.
        val random = Random(7)
        noteDisplaySchedule(NoteDisplay.STAFF, stepCount = 50, random = random)

        assertEquals(Random(7).nextInt(), random.nextInt())
    }

    @Test
    fun `random picks name or notation, never both`() {
        val schedule = noteDisplaySchedule(NoteDisplay.RANDOM, stepCount = 200, random = Random(1))

        assertEquals(200, schedule.size)
        assertTrue(schedule.all { it == NoteDisplay.NAME || it == NoteDisplay.STAFF })
        assertTrue(schedule.toSet().size == 2, "both presentations turn up")
    }

    @Test
    fun `random is decided once, not on every read`() {
        val schedule = noteDisplaySchedule(NoteDisplay.RANDOM, stepCount = 8, random = Random(3))

        // Reading the same note repeatedly is what a running screen does many times a second.
        repeat(5) { assertEquals(schedule[2], schedule.displayAt(2)) }
    }

    @Test
    fun `a seed reproduces the same exercise`() {
        assertEquals(
            noteDisplaySchedule(NoteDisplay.RANDOM, stepCount = 20, random = Random(42)),
            noteDisplaySchedule(NoteDisplay.RANDOM, stepCount = 20, random = Random(42)),
        )
    }

    @Test
    fun `past the last note there is nothing to show`() {
        val schedule = noteDisplaySchedule(NoteDisplay.NAME, stepCount = 2)

        assertEquals(NoteDisplay.NAME, schedule.displayAt(1))
        assertEquals(NoteDisplay.STAFF, schedule.displayAt(2))
        assertEquals(NoteDisplay.STAFF, schedule.displayAt(9, fallback = NoteDisplay.STAFF))
    }

    @Test
    fun `an empty exercise has an empty schedule`() {
        assertTrue(noteDisplaySchedule(NoteDisplay.RANDOM, stepCount = 0).isEmpty())
        assertFailsWith<IllegalArgumentException> {
            noteDisplaySchedule(NoteDisplay.NAME, stepCount = -1)
        }
    }

    @Test
    fun `only random has to be resolved`() {
        assertTrue(NoteDisplay.RANDOM.isRandom)
        listOf(NoteDisplay.NAME, NoteDisplay.STAFF).forEach {
            assertFalse(it.isRandom)
        }
    }

    @Test
    fun `an unresolved random reads as notation`() {
        // Setup screens and previews have no exercise to resolve against, and there is no longer a
        // both to fall back to: one presentation at a time, and unresolved means the staff.
        assertFalse(NoteDisplay.RANDOM.showsName)
        assertTrue(NoteDisplay.RANDOM.showsStaff)
    }

    @Test
    fun `never two presentations at once`() {
        NoteDisplay.entries.forEach {
            assertFalse(it.showsName && it.showsStaff, "$it shows a note twice over")
        }
    }

    @Test
    fun `a stored preference survives the option that was removed`() {
        // The persisted value is the enum name. Name and notation together used to be one of them;
        // whoever chose it wanted to read notation, so that is what they get rather than a reset.
        assertEquals(NoteDisplay.STAFF, StoredSettings.noteDisplayOverrideOf("BOTH"))
        listOf("NAME", "STAFF", "RANDOM").forEach { stored ->
            assertEquals(stored, StoredSettings.noteDisplayOverrideOf(stored)?.name)
        }
        assertNull(StoredSettings.noteDisplayOverrideOf("SOMETHING_ELSE"))
    }
}

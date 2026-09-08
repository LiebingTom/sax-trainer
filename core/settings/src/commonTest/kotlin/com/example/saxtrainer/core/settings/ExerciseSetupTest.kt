package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a remembered setup reads as, including everything it could be handed instead. */
class ExerciseSetupTest {

    @Test
    fun `a setup reads back what was put into it`() {
        val setup = exerciseSetup {
            put("mode", "STAFF")
            put("rounds", 3)
            put("hold", 1.5)
            put("mixed", true)
            put("naming", NoteDisplay.NAME)
            putInts("notes", listOf(60, 62, 64))
        }

        assertEquals("STAFF", setup.string("mode"))
        assertEquals(3, setup.int("rounds", 1))
        assertEquals(1.5, setup.double("hold", 0.0))
        assertTrue(setup.boolean("mixed", false))
        assertEquals(NoteDisplay.NAME, setup.enum("naming", NoteDisplay.entries, NoteDisplay.STAFF))
        assertEquals(listOf(60, 62, 64), setup.ints("notes"))
    }

    @Test
    fun `anything missing reads as the default`() {
        val setup = ExerciseSetup.EMPTY

        assertTrue(setup.isEmpty)
        assertNull(setup.string("mode"))
        assertEquals(4, setup.int("rounds", 4))
        assertEquals(2.0, setup.double("hold", 2.0))
        assertTrue(setup.boolean("mixed", true))
        assertEquals(NoteDisplay.STAFF, setup.enum("naming", NoteDisplay.entries, NoteDisplay.STAFF))
        assertNull(setup.ints("notes"), "never chosen is not the same as chosen and empty")
    }

    @Test
    fun `a value this version cannot read is the default rather than a failure`() {
        val setup = ExerciseSetup(
            mapOf(
                "rounds" to "several",
                "hold" to "",
                "mixed" to "yes",
                "naming" to "SEMAPHORE",
                "notes" to "60,,sixty-two,64",
            ),
        )

        assertEquals(1, setup.int("rounds", 1))
        assertEquals(0.0, setup.double("hold", 0.0))
        assertTrue(setup.boolean("mixed", true))
        assertEquals(NoteDisplay.STAFF, setup.enum("naming", NoteDisplay.entries, NoteDisplay.STAFF))
        assertEquals(listOf(60, 64), setup.ints("notes"), "the readable notes survive the rest")
    }

    @Test
    fun `an empty list of notes is told apart from no list at all`() {
        val setup = exerciseSetup { putInts("notes", emptyList()) }

        assertEquals(emptyList(), setup.ints("notes"))
    }

    @Test
    fun `a setup survives being written down and read back`() {
        val setup = exerciseSetup {
            put("sequence", "major_scale")
            put("tempo", 96)
            put("check", false)
            putInts("notes", listOf(58, 60))
        }

        val restored = StoredExerciseSetup.decode(StoredExerciseSetup.encode(setup))

        assertEquals("major_scale", restored.string("sequence"))
        assertEquals(96, restored.int("tempo", 0))
        assertEquals(false, restored.boolean("check", true))
        assertEquals(listOf(58, 60), restored.ints("notes"))
    }

    @Test
    fun `a chosen set of notes survives being written down and read back`() {
        // What a note-picking dialog produces: a handful of notes, or none at all.
        val chosen = listOf(58, 60, 62, 63, 65, 67, 69, 70, 72)

        val restored = StoredExerciseSetup.decode(
            StoredExerciseSetup.encode(exerciseSetup { putInts("custom_notes", chosen) }),
        )
        val emptied = StoredExerciseSetup.decode(
            StoredExerciseSetup.encode(exerciseSetup { putInts("custom_notes", emptyList()) }),
        )

        assertEquals(chosen, restored.ints("custom_notes"), "in the order they were written")
        assertEquals(emptyList(), emptied.ints("custom_notes"), "no notes chosen is still a choice")
    }

    @Test
    fun `two exercises keep their own notes`() {
        val repository = InMemoryExerciseSetupRepository()

        repository.remember(Exercise.LONG_TONES, exerciseSetup { putInts("custom_notes", listOf(60)) })
        repository.remember(
            Exercise.NOTE_TRAINER,
            exerciseSetup { putInts("custom_notes", listOf(62, 64)) },
        )

        assertEquals(listOf(60), repository.setupOf(Exercise.LONG_TONES).ints("custom_notes"))
        assertEquals(listOf(62, 64), repository.setupOf(Exercise.NOTE_TRAINER).ints("custom_notes"))
    }

    @Test
    fun `stored text that makes no sense reads as the entries that do`() {
        val restored = StoredExerciseSetup.decode("tempo=96\nrubbish\n=nameless\nsequence=arpeggio")

        assertEquals(96, restored.int("tempo", 0))
        assertEquals("arpeggio", restored.string("sequence"))
    }

    @Test
    fun `nothing stored is an empty setup`() {
        assertTrue(StoredExerciseSetup.decode(null).isEmpty)
        assertTrue(StoredExerciseSetup.decode("").isEmpty)
        assertTrue(StoredExerciseSetup.decode("rubbish").isEmpty)
    }

    @Test
    fun `every exercise is stored under a name of its own`() {
        val keys = Exercise.entries.map(StoredExerciseSetup::keyOf)

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it == StoredSettings.KEY_INSTRUMENT })
    }

    @Test
    fun `a setup kept in memory is given back for the exercise it belongs to`() {
        val repository = InMemoryExerciseSetupRepository()

        repository.remember(Exercise.METRONOME, exerciseSetup { put("tempo", 132) })

        assertEquals(132, repository.setupOf(Exercise.METRONOME).int("tempo", 0))
        assertTrue(repository.setupOf(Exercise.SEQUENCE).isEmpty, "one exercise, one setup")
    }
}

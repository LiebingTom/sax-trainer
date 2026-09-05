package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.beamGroups
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.notatedDurationOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The predefined material, checked as music rather than as data. */
class SequenceLibraryTest {

    private fun labels(
        definition: SequenceDefinition,
        naming: NoteNaming = NoteNaming.INTERNATIONAL,
    ) = definition.steps.map { naming.labelOf(it.note, definition.accidentalStyle) }

    @Test
    fun `every entry is complete and uniquely named`() {
        assertTrue(SequenceLibrary.all.isNotEmpty())
        assertEquals(
            SequenceLibrary.all.size,
            SequenceLibrary.all.map { it.id }.distinct().size,
        )
        SequenceLibrary.all.forEach { definition ->
            assertTrue(definition.name.isNotBlank())
            assertTrue(definition.description.isNotBlank())
            assertTrue(definition.noteCount > 0)
            assertEquals(definition, SequenceLibrary.byId(definition.id))
        }
    }

    @Test
    fun `C major is the plain octave`() {
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            labels(SequenceLibrary.cMajorUp),
        )
    }

    @Test
    fun `played back down, a scale comes back to where it started`() {
        val sequence = SequenceLibrary.cMajorUp.build(addInverse = true)
        val notes = sequence.steps.map {
            NoteNaming.INTERNATIONAL.labelOf(it.note, sequence.accidentalStyle)
        }

        assertEquals(15, notes.size)
        assertEquals("C4", notes.first())
        assertEquals("C5", notes[7])
        assertEquals("C4", notes.last())
        assertEquals("B4", notes[8], "the turning point is not played twice")
    }

    @Test
    fun `repeating plays the whole thing again`() {
        val once = SequenceLibrary.cMajorUp.build()
        val fiveTimes = SequenceLibrary.cMajorUp.build(repeats = 5)

        assertEquals(once.steps.size * 5, fiveTimes.steps.size)
        assertEquals(once.steps, fiveTimes.steps.take(once.steps.size))
        assertEquals(once.steps, fiveTimes.steps.takeLast(once.steps.size))
        assertEquals(once.totalBeats.value * 5, fiveTimes.totalBeats.value, 1e-9)
    }

    @Test
    fun `a sequence is played at least once`() {
        assertFailsWith<IllegalArgumentException> {
            SequenceLibrary.cMajorUp.build(repeats = 0)
        }
    }

    @Test
    fun `G major has its F sharp`() {
        val notes = labels(SequenceLibrary.gMajorUp)

        assertEquals(listOf("G4", "A4", "B4", "C5", "D5", "E5", "F♯5", "G5"), notes)
        val placement = TrebleStaff.placementOf(
            Note(NoteName.F_SHARP, 5),
            SequenceLibrary.gMajorUp.accidentalStyle,
        )
        assertEquals(Accidental.SHARP, placement.accidental)
    }

    @Test
    fun `F major is spelled with a B flat, never an A sharp`() {
        val definition = SequenceLibrary.fMajorUp

        assertEquals(AccidentalStyle.FLATS, definition.accidentalStyle)
        assertEquals(listOf("F4", "G4", "A4", "B♭4", "C5", "D5", "E5", "F5"), labels(definition))

        // And it is drawn where a B flat belongs: on the B line, with a flat in front of it.
        val flatNote = definition.steps[3].note
        val placement = TrebleStaff.placementOf(flatNote, definition.accidentalStyle)
        val bNatural = TrebleStaff.placementOf(Note(NoteName.B, 4), definition.accidentalStyle)
        assertEquals(Accidental.FLAT, placement.accidental)
        assertEquals(
            bNatural.stepsFromBottomLine,
            placement.stepsFromBottomLine,
            "a B flat shares its line with B, not with A",
        )
    }

    @Test
    fun `the same scales read differently under German naming`() {
        // German calls B flat "B" and B natural "H", which is the whole point of the setting.
        assertEquals(
            listOf("F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5"),
            labels(SequenceLibrary.fMajorUp, NoteNaming.GERMAN),
        )
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "H4", "C5"),
            labels(SequenceLibrary.cMajorUp, NoteNaming.GERMAN),
        )
        assertEquals(
            listOf("G4", "A4", "H4", "C5", "D5", "E5", "Fis5", "G5"),
            labels(SequenceLibrary.gMajorUp, NoteNaming.GERMAN),
        )
    }

    @Test
    fun `a scale is plain quarter notes, two bars of them`() {
        val sequence = SequenceLibrary.cMajorUp.build()

        sequence.steps.forEach { assertEquals(Beats(1.0), it.duration) }
        assertEquals(Beats(8.0), sequence.totalBeats)
        assertEquals(0, sequence.totalBeats.value.toInt() % sequence.timeSignature.beatsPerBar)
    }

    @Test
    fun `beat positions follow the durations`() {
        val sequence = SequenceLibrary.dottedSteps.build()

        assertEquals(Beats(0.0), sequence.startBeatOf(0))
        assertEquals(Beats(1.5), sequence.startBeatOf(1))
        assertEquals(Beats(2.0), sequence.startBeatOf(2))
        assertEquals(Beats(2.5), sequence.startBeatOf(3))
        assertEquals(Beats(3.0), sequence.startBeatOf(4))
        assertEquals(Beats(4.0), sequence.totalBeats, "one bar of four four")
    }

    @Test
    fun `every note has a written form`() {
        // Nothing in the library needs a tie, which this app cannot yet draw.
        SequenceLibrary.all.forEach { definition ->
            val sequence = definition.build()
            sequence.steps.indices.forEach { index ->
                assertNotNull(
                    sequence.notatedDurationOf(index),
                    "${definition.name} note ${index + 1} has no written form",
                )
            }
        }
    }

    @Test
    fun `written straight through, no note is split across a bar line`() {
        // Splitting one needs a tie, which this app does not write, so the material avoids it.
        SequenceLibrary.all.forEach { definition ->
            val sequence = definition.build()
            val beatsPerBar = sequence.timeSignature.beatsPerBar
            sequence.steps.indices.forEach { index ->
                val start = sequence.startBeatOf(index).value
                val end = sequence.endBeatOf(index).value
                val bar = (start / beatsPerBar).toInt()
                assertTrue(
                    end <= (bar + 1) * beatsPerBar + 1e-9,
                    "${definition.name} note ${index + 1} spans a bar line",
                )
            }
        }
    }

    @Test
    fun `a scale stays bar aligned however it is shaped`() {
        // Every note of a scale is one beat, so no bar line can ever fall inside one of them.
        SequenceLibrary.byCategory.getValue(SequenceCategory.SCALE).forEach { definition ->
            listOf(false, true).forEach { inverse ->
                (1..5).forEach { repeats ->
                    val sequence = definition.build(addInverse = inverse, repeats = repeats)
                    val beatsPerBar = sequence.timeSignature.beatsPerBar
                    sequence.steps.indices.forEach { index ->
                        val start = sequence.startBeatOf(index).value
                        val end = sequence.endBeatOf(index).value
                        val bar = (start / beatsPerBar).toInt()
                        assertTrue(
                            end <= (bar + 1) * beatsPerBar + 1e-9,
                            "${definition.name} (inverse=$inverse, repeats=$repeats) " +
                                "note ${index + 1} spans a bar line",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a pattern whose length is not a whole bar drifts when it is repeated`() {
        // Documented rather than prevented: the music is right, and the engraving draws the bar line
        // before the note that spans it because it cannot tie one. Preventing this would mean
        // padding every pattern out to a whole bar with a rest, which the app cannot write either.
        val sequence = SequenceLibrary.dottedSteps.build(addInverse = true, repeats = 2)

        assertEquals(
            14.0,
            sequence.totalBeats.value,
            1e-9,
            "one pass is seven beats, not a whole number of bars",
        )
        val secondPassStart = sequence.startBeatOf(SequenceLibrary.dottedSteps.noteCount * 2 - 1)
        assertTrue(secondPassStart.value % 4.0 != 0.0)
    }

    @Test
    fun `the dotted pattern is not a grid of equal notes`() {
        val sequence = SequenceLibrary.dottedSteps.build()
        val written = sequence.steps.indices.map { sequence.notatedDurationOf(it)!! }

        assertEquals(1, written.first().dots)
        assertTrue(written.any { it.value == NoteValue.EIGHTH })
        assertTrue(sequence.beamGroups().isNotEmpty(), "something is worth beaming")
    }

    @Test
    fun `patterns and scales are both offered`() {
        assertTrue(SequenceLibrary.byCategory.containsKey(SequenceCategory.SCALE))
        assertTrue(SequenceLibrary.byCategory.containsKey(SequenceCategory.PATTERN))
        assertEquals(SequenceLibrary.all.size, SequenceLibrary.byCategory.values.sumOf { it.size })
    }
}

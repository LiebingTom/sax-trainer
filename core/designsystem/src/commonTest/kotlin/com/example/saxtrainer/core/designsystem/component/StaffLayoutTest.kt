package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the notes of a sequence end up.
 *
 * Positions are worked out without a canvas, so they can be checked here rather than by eye.
 */
class StaffLayoutTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER)
    private val half = NotatedDuration(NoteValue.HALF)
    private val eighth = NotatedDuration(NoteValue.EIGHTH)
    private val dottedQuarter = NotatedDuration(NoteValue.QUARTER, dots = 1)

    private fun sequence(
        vararg steps: Pair<Note, NotatedDuration>,
        accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    ) = ToneSequence(
        steps = steps.map { ToneStep(it.first, it.second.beats()) },
        accidentalStyle = accidentalStyle,
    )

    private fun note(name: NoteName, octave: Int = 4) = Note(name, octave)

    @Test
    fun `every note is placed, left to right`() {
        val layout = StaffLayoutBuilder.of(
            sequence(
                note(NoteName.C) to quarter,
                note(NoteName.D) to quarter,
                note(NoteName.E) to quarter,
            )
        )

        assertEquals(3, layout.notes.size)
        assertEquals(listOf(0, 1, 2), layout.notes.map { it.index })
        layout.notes.zipWithNext { earlier, later ->
            assertTrue(earlier.headXSpaces < later.headXSpaces)
        }
        assertTrue(
            layout.notes.last().headXSpaces < layout.widthSpaces,
            "nothing sits past the edge",
        )
    }

    @Test
    fun `a longer note takes more room than a shorter one`() {
        val layout = StaffLayoutBuilder.of(
            sequence(
                note(NoteName.C) to eighth,
                note(NoteName.D) to eighth,
                note(NoteName.E) to half,
                note(NoteName.F) to quarter,
            )
        )
        val gaps = layout.notes.zipWithNext { a, b -> b.headXSpaces - a.headXSpaces }

        assertTrue(gaps[0] < gaps[1], "two eighths are close together")
        assertTrue(gaps[2] > gaps[0], "a half note is given room")
    }

    @Test
    fun `the written value comes with the placement`() {
        val layout = StaffLayoutBuilder.of(
            sequence(note(NoteName.C) to dottedQuarter, note(NoteName.D) to eighth)
        )

        assertEquals(NoteValue.QUARTER, layout.notes[0].notated!!.value)
        assertEquals(1, layout.notes[0].notated!!.dots)
        assertEquals(NoteValue.EIGHTH, layout.notes[1].notated!!.value)
    }

    @Test
    fun `a duration with no written form is placed anyway`() {
        val layout = StaffLayoutBuilder.of(
            ToneSequence(steps = listOf(ToneStep(note(NoteName.A), com.example.saxtrainer.core.music.Beats(5.0))))
        )

        assertEquals(1, layout.notes.size)
        assertNull(layout.notes.single().notated, "nothing is invented for it")
    }

    @Test
    fun `a bar line falls where a bar starts, and never before the first note`() {
        // Five quarters: the fifth begins bar two.
        val layout = StaffLayoutBuilder.of(
            sequence(*Array(5) { note(NoteName.entries[it]) to quarter })
        )

        assertEquals(1, layout.barLineXSpaces.size)
        val barLine = layout.barLineXSpaces.single()
        assertTrue(layout.notes[3].headXSpaces < barLine)
        assertTrue(barLine < layout.notes[4].headXSpaces)
    }

    @Test
    fun `an accidental is given room in front of its note`() {
        val plain = StaffLayoutBuilder.of(
            sequence(note(NoteName.C) to quarter, note(NoteName.D) to quarter)
        )
        val altered = StaffLayoutBuilder.of(
            sequence(note(NoteName.C_SHARP) to quarter, note(NoteName.D) to quarter)
        )

        assertEquals(
            Accidental.SHARP,
            altered.notes.first().placement.accidental,
        )
        assertTrue(
            altered.notes.first().headXSpaces > plain.notes.first().headXSpaces,
            "the sharp pushes everything right",
        )
    }

    @Test
    fun `the spelling of the sequence decides the accidental`() {
        val flats = StaffLayoutBuilder.of(
            sequence(note(NoteName.A_SHARP) to quarter, accidentalStyle = AccidentalStyle.FLATS)
        )
        val sharps = StaffLayoutBuilder.of(sequence(note(NoteName.A_SHARP) to quarter))

        assertEquals(Accidental.FLAT, flats.notes.single().placement.accidental)
        assertEquals(Accidental.SHARP, sharps.notes.single().placement.accidental)
        assertTrue(
            flats.notes.single().placement.stepsFromBottomLine >
                sharps.notes.single().placement.stepsFromBottomLine,
            "a B flat sits a step above an A sharp",
        )
    }

    @Test
    fun `beam groups come from the music and are reachable by note`() {
        val layout = StaffLayoutBuilder.of(
            sequence(
                note(NoteName.C) to eighth,
                note(NoteName.D) to eighth,
                note(NoteName.E) to quarter,
            )
        )

        assertEquals(listOf(0..1), layout.beamGroups)
        assertNotNull(layout.beamGroupOf(0))
        assertNull(layout.beamGroupOf(2), "a quarter is never in a group")
    }

    @Test
    fun `the drawing leaves room above and below for beams`() {
        val sequence = sequence(note(NoteName.C) to eighth, note(NoteName.D) to eighth)
        val layout = StaffLayoutBuilder.of(sequence)
        val bare = staffStepRange(sequence.notes, sequence.accidentalStyle)

        assertTrue(layout.stepRange.first < bare.first)
        assertTrue(layout.stepRange.last > bare.last)
    }
}

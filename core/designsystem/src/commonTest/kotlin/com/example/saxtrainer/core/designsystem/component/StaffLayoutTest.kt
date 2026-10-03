package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.saxtrainer.core.music.Beats
import kotlin.test.assertFalse

/**
 * Where the notes of a sequence end up.
 *
 * Positions are worked out without a canvas, so they can be checked here rather than by eye.
 */
class StaffLayoutTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER)
    private val half = NotatedDuration(NoteValue.HALF)
    private val eighth = NotatedDuration(NoteValue.EIGHTH)
    private val sixteenth = NotatedDuration(NoteValue.SIXTEENTH)
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
    fun `a note held across a bar line is written as two notes, tied`() {
        // Five beats of four-four: a whole note, the bar line, then a quarter still sounding.
        val layout = StaffLayoutBuilder.of(
            ToneSequence(steps = listOf(ToneStep(note(NoteName.A), Beats(5.0))))
        )

        assertEquals(2, layout.notes.size, "one sounding note, two written ones")
        assertEquals(NoteValue.WHOLE, layout.notes[0].notated!!.value)
        assertEquals(NoteValue.QUARTER, layout.notes[1].notated!!.value)
        assertTrue(layout.notes[0].tiedToNext, "the first is held into the second")
        assertFalse(layout.notes[1].tiedToNext, "and the second ends it")
        assertEquals(1, layout.barLineXSpaces.size, "the bar line it crosses is still drawn")
        // Both halves are the same note of the exercise, so a caller colours them together.
        assertEquals(listOf(0, 0), layout.notes.map { it.index })
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

    // --- the block at the head of the staff -------------------------------------------------

    @Test
    fun `without a block nothing has moved`() {
        val music = sequence(note(NoteName.C) to quarter, note(NoteName.D_SHARP) to eighth)
        val layout = StaffLayoutBuilder.of(music, StaffPrefix.None)

        assertEquals(0f, layout.prefixWidthSpaces)
        // The positions the engine has always produced, kept as literals so a change to any of the
        // new parameters cannot quietly move an existing caller's notes: 1.2 of leading and 0.75 to
        // the head, then a quarter note's 4.2 of advance and 1.4 for the sharp.
        assertEquals(1.95f, layout.notes[0].headXSpaces, TOLERANCE)
        assertEquals(7.55f, layout.notes[1].headXSpaces, TOLERANCE)
    }

    @Test
    fun `the block pushes every note right by exactly its width`() {
        val music = sequence(note(NoteName.C) to quarter, note(NoteName.E) to quarter)
        val bare = StaffLayoutBuilder.of(music, StaffPrefix.None)
        val withClef = StaffLayoutBuilder.of(music, StaffPrefix.Treble)

        assertEquals(StaffPrefix.DEFAULT_WIDTH_SPACES, withClef.prefixWidthSpaces)
        bare.notes.zip(withClef.notes) { plain, shifted ->
            assertEquals(
                plain.headXSpaces + StaffPrefix.DEFAULT_WIDTH_SPACES,
                shifted.headXSpaces,
                TOLERANCE,
            )
        }
    }

    @Test
    fun `an empty block is as wide as a full one`() {
        val music = sequence(note(NoteName.C) to quarter)
        val withClef = StaffLayoutBuilder.of(music, StaffPrefix.Treble)
        val withoutClef = StaffLayoutBuilder.of(music, StaffPrefix.Reserved)

        // The whole point of the block: what is in it is not allowed to move the music.
        assertEquals(withClef.prefixWidthSpaces, withoutClef.prefixWidthSpaces)
        assertEquals(
            withClef.notes.single().headXSpaces,
            withoutClef.notes.single().headXSpaces,
            TOLERANCE,
        )
    }

    @Test
    fun `a clef makes room for itself even when every note is inside the staff`() {
        // G4 and B4 sit on the staff, so nothing but the clef can be asking for the extra room.
        val music = sequence(note(NoteName.G) to quarter, note(NoteName.B) to quarter)
        val bare = StaffLayoutBuilder.of(music, StaffPrefix.None)
        val withClef = StaffLayoutBuilder.of(music, StaffPrefix.Treble)

        assertTrue(withClef.stepRange.first <= bare.stepRange.first)
        assertTrue(withClef.stepRange.last >= bare.stepRange.last)
        assertTrue(CLEF_STEP_RANGE.first in withClef.stepRange, "the tail is not clipped")
        assertTrue(CLEF_STEP_RANGE.last in withClef.stepRange, "the curl is not clipped")
    }

    // --- notes written as their name ----------------------------------------------------------

    @Test
    fun `a written name sits in the middle of the room reserved for it`() {
        val layout = StaffLayoutBuilder.of(
            sequence(note(NoteName.C) to quarter, note(NoteName.E) to quarter),
            styles = listOf(StaffNoteStyle.NAME, StaffNoteStyle.HEAD),
            labelWidthSpaces = LABEL_WIDTH,
        )

        assertEquals(listOf(StaffNoteStyle.NAME, StaffNoteStyle.HEAD), layout.notes.map { it.style })
        // 1.2 of leading, then half of the reserved box.
        assertEquals(1.2f + LABEL_WIDTH / 2f, layout.notes[0].headXSpaces, TOLERANCE)
    }

    @Test
    fun `every written name takes the same room, however long its own name is`() {
        fun placed(vararg names: NoteName) = StaffLayoutBuilder.of(
            sequence(*names.map { note(it) to quarter }.toTypedArray()),
            styles = names.map { StaffNoteStyle.NAME },
            labelWidthSpaces = LABEL_WIDTH,
        ).notes.map { it.headXSpaces }

        // "C#4" is wider than "C4" and a sharp is drawn where a plain note has nothing, but a name
        // is given the reserved width either way, so neither fact moves anything.
        assertEquals(placed(NoteName.C, NoteName.E), placed(NoteName.C_SHARP, NoteName.E))
        assertEquals(placed(NoteName.C, NoteName.E), placed(NoteName.E, NoteName.C_SHARP))
    }

    @Test
    fun `a short note is never given less room than its name needs`() {
        val music = sequence(note(NoteName.C) to sixteenth, note(NoteName.E) to sixteenth)
        val drawn = StaffLayoutBuilder.of(music)
        val written = StaffLayoutBuilder.of(
            music,
            styles = listOf(StaffNoteStyle.NAME, StaffNoteStyle.NAME),
            labelWidthSpaces = LABEL_WIDTH,
        )
        fun gap(layout: StaffLayout) = layout.notes[1].headXSpaces - layout.notes[0].headXSpaces

        // Two sixteenths sit closer together than a name is wide, so the name wins the argument.
        assertTrue(gap(drawn) < LABEL_WIDTH)
        assertEquals(LABEL_WIDTH, gap(written), TOLERANCE)
    }

    @Test
    fun `a drawn note beside a written one is placed as it always was`() {
        val music = sequence(note(NoteName.C) to quarter, note(NoteName.E) to quarter)
        val bare = StaffLayoutBuilder.of(music)
        val mixed = StaffLayoutBuilder.of(
            music,
            styles = listOf(StaffNoteStyle.HEAD, StaffNoteStyle.HEAD),
            labelWidthSpaces = LABEL_WIDTH,
        )

        assertEquals(bare.notes.map { it.headXSpaces }, mixed.notes.map { it.headXSpaces })
    }

    @Test
    fun `a written name is given room above and below, and heads alone are not`() {
        val music = sequence(note(NoteName.G) to quarter, note(NoteName.B) to quarter)
        val drawn = StaffLayoutBuilder.of(music)
        val written = StaffLayoutBuilder.of(
            music,
            styles = listOf(StaffNoteStyle.NAME, StaffNoteStyle.HEAD),
            labelWidthSpaces = LABEL_WIDTH,
        )

        assertEquals(drawn.stepRange.first - 1, written.stepRange.first)
        assertEquals(drawn.stepRange.last + 1, written.stepRange.last)
    }

    @Test
    fun `a written name is not beamed, and breaks the group it was in`() {
        // Four sixteenths fall in one beat, so the music itself beams all four together.
        val four = sequence(*Array(4) { note(NoteName.entries[it]) to sixteenth })

        assertEquals(listOf(0..3), StaffLayoutBuilder.of(four).beamGroups)

        val broken = StaffLayoutBuilder.of(
            four,
            styles = listOf(
                StaffNoteStyle.HEAD,
                StaffNoteStyle.NAME,
                StaffNoteStyle.HEAD,
                StaffNoteStyle.HEAD,
            ),
            labelWidthSpaces = LABEL_WIDTH,
        )

        // The lone eighth in front of the name keeps its flag; the pair behind it is still beamed.
        assertEquals(listOf(2..3), broken.beamGroups)
        assertNull(broken.beamGroupOf(0))
        assertNull(broken.beamGroupOf(1))
    }

    private companion object {
        const val TOLERANCE = 1e-4f

        /** A stand-in for a measured name; the layout only ever gets a number. */
        const val LABEL_WIDTH = 3f
    }

    // --- rests ----------------------------------------------------------------------------------

    /** The middle line, where every rest but the whole rest is registered. */
    private val restLine = 4

    @Test
    fun `a rest is placed on the middle line and says nothing about pitch`() {
        val layout = StaffLayoutBuilder.of(
            ToneSequence(
                steps = listOf(
                    ToneStep(note(NoteName.C), quarter.beats()),
                    ToneStep.rest(quarter.beats()),
                ),
            )
        )

        val rest = layout.notes[1]
        assertTrue(rest.isRest)
        assertEquals(restLine, rest.placement.stepsFromBottomLine)
        assertEquals(Accidental.NONE, rest.placement.accidental)
        assertEquals(NoteValue.QUARTER, rest.notated!!.value, "a rest is written as a length")
        assertFalse(rest.tiedToNext, "a silence is never held over")
    }

    @Test
    fun `a rest takes exactly the room a note of the same length takes`() {
        val played = StaffLayoutBuilder.of(
            sequence(note(NoteName.C) to quarter, note(NoteName.D) to quarter),
        )
        val rested = StaffLayoutBuilder.of(
            ToneSequence(
                steps = listOf(
                    ToneStep(note(NoteName.C), quarter.beats()),
                    ToneStep.rest(quarter.beats()),
                ),
            )
        )

        assertEquals(played.widthSpaces, rested.widthSpaces)
        assertEquals(played.notes[1].headXSpaces, rested.notes[1].headXSpaces)
    }

    @Test
    fun `a bar of nothing but rests still gets a staff to draw them on`() {
        // A row of a page can be exactly this, and asking a piece with no notes in it how much
        // vertical room its notes need is the easiest way to fall over.
        val layout = StaffLayoutBuilder.of(
            ToneSequence(steps = List(4) { ToneStep.rest(quarter.beats()) }),
        )

        assertEquals(4, layout.notes.size)
        assertTrue(layout.notes.all { it.isRest })
        assertTrue(layout.stepRange.first <= 0 && layout.stepRange.last >= 8, "the five lines fit")
    }

    @Test
    fun `a rest is drawn as a rest even where the notes are written as names`() {
        val layout = StaffLayoutBuilder.of(
            sequence = ToneSequence(
                steps = listOf(
                    ToneStep(note(NoteName.C), quarter.beats()),
                    ToneStep.rest(quarter.beats()),
                ),
            ),
            styles = listOf(StaffNoteStyle.NAME, StaffNoteStyle.NAME),
            labelWidthSpaces = 3f,
        )

        assertEquals(StaffNoteStyle.NAME, layout.notes[0].style)
        assertEquals(
            StaffNoteStyle.HEAD,
            layout.notes[1].style,
            "a rest has no pitch, so it has no name to be written as",
        )
    }

    @Test
    fun `a rest breaks a beam rather than joining it`() {
        val layout = StaffLayoutBuilder.of(
            ToneSequence(
                steps = listOf(
                    ToneStep(note(NoteName.C), eighth.beats()),
                    ToneStep.rest(eighth.beats()),
                    ToneStep(note(NoteName.D), eighth.beats()),
                    ToneStep(note(NoteName.E), eighth.beats()),
                ),
            )
        )

        assertEquals(listOf(2..3), layout.beamGroups)
    }

    // --- written spelling ---------------------------------------------------------------------

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `a note written with its own spelling is drawn on its own letter, whatever the sequence's style`() {
        val tokens = listOf("Eb4", "D#4", "Cb5", "B#4", "E#4", "Fb4")
        val layout = StaffLayoutBuilder.of(
            ToneSequence(
                steps = tokens.map { ToneStep.of(written(it), quarter.beats()) },
                accidentalStyle = AccidentalStyle.SHARPS,
            ),
        )

        assertEquals(
            tokens.map { TrebleStaff.placementOf(written(it)) },
            layout.notes.map { it.placement },
        )
        assertEquals(
            listOf(Accidental.FLAT, Accidental.SHARP, Accidental.FLAT, Accidental.SHARP, Accidental.SHARP, Accidental.FLAT),
            layout.notes.map { it.placement.accidental },
        )
    }

    @Test
    fun `the staff is sized for the notes as written`() {
        // C flat 6 is written on the C line above the staff, a step higher than the B5 it sounds as.
        val cFlat = StaffLayoutBuilder.of(ToneSequence(steps = listOf(ToneStep.of(written("Cb6"), quarter.beats()))))
        val b = StaffLayoutBuilder.of(ToneSequence(steps = listOf(ToneStep(Note(NoteName.B, 5), quarter.beats()))))

        assertTrue(cFlat.stepRange.last > b.stepRange.last)
    }

    // --- justification ---------------------------------------------------------------------------

    private fun fourQuarters() = sequence(
        note(NoteName.C) to quarter,
        note(NoteName.D) to quarter,
        note(NoteName.E) to quarter,
        note(NoteName.G) to quarter,
    )

    @Test
    fun `a justified row spreads its music to the width and leaves the clef where it is`() {
        val natural = StaffLayoutBuilder.of(fourQuarters(), StaffPrefix.Treble)
        val target = natural.widthSpaces * 1.5f

        val justified = natural.justifiedTo(target)

        assertEquals(target, justified.widthSpaces)
        assertEquals(natural.prefixWidthSpaces, justified.prefixWidthSpaces)
        val gaps = { layout: StaffLayout -> layout.notes.zipWithNext { a, b -> b.headXSpaces - a.headXSpaces } }
        gaps(natural).zip(gaps(justified)).forEach { (before, after) -> assertTrue(after > before) }
    }

    @Test
    fun `a row may close up a little to fit, but no further`() {
        val natural = StaffLayoutBuilder.of(fourQuarters(), StaffPrefix.Treble)
        val music = natural.widthSpaces - natural.prefixWidthSpaces

        val closer = natural.justifiedTo(natural.prefixWidthSpaces + music * 0.9f)
        assertTrue(closer.widthSpaces < natural.widthSpaces)

        val tooTight = natural.justifiedTo(natural.prefixWidthSpaces + music * 0.5f)
        assertEquals(natural, tooTight, "squeezed past the tightest spacing it is left alone")
    }
}

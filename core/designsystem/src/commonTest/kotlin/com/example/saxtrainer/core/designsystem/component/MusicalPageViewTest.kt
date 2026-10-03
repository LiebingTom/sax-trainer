package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.bars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a piece is broken into the rows of a page.
 *
 * The packing is worked out from the layout alone, without a canvas, so what a given width does to
 * a given piece can be checked here rather than by looking at a phone.
 */
class MusicalPageViewTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER).beats()

    private val firstPrefix = StaffPrefix.treble(TimeSignature.FOUR_FOUR)

    /** As wide as the first block but with no time signature in it, exactly as the page does. */
    private val laterPrefix = StaffPrefix(
        clef = true,
        timeSignature = null,
        widthSpaces = firstPrefix.widthSpaces,
    )

    /**
     * Plain notes, deliberately: an accidental takes room of its own, so a bar of sharps is wider
     * than a bar without them and the packing would then be about the spelling rather than the
     * width. What is under test here is how many bars fit, not how wide a sharp is.
     */
    private val naturals = listOf(NoteName.C, NoteName.D, NoteName.E, NoteName.G, NoteName.A)

    private fun note(index: Int) = Note(naturals[index % naturals.size], 4)

    /** [count] bars of four quarter notes: the exercise the layout is aimed at. */
    private fun quarterBars(count: Int) = ToneSequence(
        steps = List(count * 4) { ToneStep(note(it), quarter) },
    )

    private fun rowsOf(sequence: ToneSequence, widthSpaces: Float) =
        systemsOf(sequence, widthSpaces, firstPrefix, laterPrefix)

    /** How much room one row of [count] bars takes at its natural spacing, asked of the layout. */
    private fun naturalSpacesFor(sequence: ToneSequence, count: Int): Float {
        val bars = sequence.bars()
        val steps = bars.first().first..bars[count - 1].last
        return StaffLayoutBuilder.of(
            sequence.copy(steps = sequence.steps.slice(steps)),
            firstPrefix,
        ).widthSpaces
    }

    /**
     * The least room one row of [count] bars needs: a row of a page is justified, and may close up
     * to [TIGHTEST_JUSTIFICATION] of its natural spacing.
     */
    private fun spacesFor(sequence: ToneSequence, count: Int): Float =
        firstPrefix.widthSpaces + (naturalSpacesFor(sequence, count) - firstPrefix.widthSpaces) * TIGHTEST_JUSTIFICATION

    @Test
    fun `a page wide enough for two bars puts two bars on a row`() {
        val music = quarterBars(4)

        val rows = rowsOf(music, spacesFor(music, 2))

        assertEquals(listOf(0, 8), rows.map { it.firstStep })
        assertEquals(listOf(8, 8), rows.map { it.sequence.steps.size })
    }

    @Test
    fun `a narrower page takes fewer bars, never part of one`() {
        val music = quarterBars(4)

        val rows = rowsOf(music, spacesFor(music, 2) - 1f)

        assertEquals(listOf(0, 4, 8, 12), rows.map { it.firstStep })
    }

    @Test
    fun `a wider page uses the room it has`() {
        val music = quarterBars(4)

        val rows = rowsOf(music, spacesFor(music, 4))

        assertEquals(1, rows.size, "all four bars fit on one row")
        assertEquals(16, rows.single().sequence.steps.size)
    }

    @Test
    fun `a bar too wide for the page still gets a row of its own`() {
        // Cutting it in half would be worse: a row always holds whole bars.
        val music = quarterBars(2)

        val rows = rowsOf(music, 1f)

        assertEquals(listOf(0, 4), rows.map { it.firstStep })
    }

    @Test
    fun `the rows cover every step, in order, once`() {
        val music = quarterBars(5)

        val rows = rowsOf(music, spacesFor(music, 2))

        assertEquals(music.steps, rows.flatMap { it.sequence.steps })
        assertEquals(music.steps.indices.toList(), rows.flatMap { it.steps.toList() })
    }

    @Test
    fun `a row keeps the music's own time signature and spelling`() {
        val music = quarterBars(4)

        val rows = rowsOf(music, spacesFor(music, 2))

        assertTrue(rows.all { it.sequence.timeSignature == music.timeSignature })
        assertTrue(rows.all { it.sequence.accidentalStyle == music.accidentalStyle })
    }

    @Test
    fun `a row never begins anywhere but on a bar line`() {
        val music = quarterBars(6)

        val rows = rowsOf(music, spacesFor(music, 2))
        val barStarts = music.bars().map { it.first }

        assertTrue(rows.all { it.firstStep in barStarts })
    }

    @Test
    fun `rests are laid out on the page like anything else`() {
        val music = ToneSequence(
            steps = List(4) { ToneStep(note(it), quarter) } +
                List(4) { if (it % 2 == 0) ToneStep(note(it), quarter) else ToneStep.rest(quarter) },
        )

        val rows = rowsOf(music, spacesFor(music, 1) - 1f)

        assertEquals(listOf(0, 4), rows.map { it.firstStep })
        assertEquals(2, rows.last().sequence.steps.count { it.isRest })
    }

    @Test
    fun `a row may close up a little to keep its bars together`() {
        val piece = quarterBars(2)
        val natural = naturalSpacesFor(piece, 2)
        val music = natural - firstPrefix.widthSpaces

        // Narrower than two bars at their natural spacing, but within what a justified row allows.
        val rows = rowsOf(piece, firstPrefix.widthSpaces + music * 0.9f)

        assertEquals(1, rows.size, "both bars still on one row")
    }
}

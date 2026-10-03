package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sequence as it would be written down, with rests in it.
 *
 * A sounding note and a written note are two different things, and a silence is a third: it is cut
 * at a bar line like a note, written with the values that exist like a note, and - unlike a note -
 * never tied, because nothing is being held over.
 */
class NotatedReadingTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER).beats()

    private fun note(index: Int) = Note(NoteName.entries[index % 12], 4)

    @Test
    fun `a rest is read as a rest of its own written value`() {
        val reading = ToneSequence(
            steps = listOf(ToneStep(note(0), quarter), ToneStep.rest(quarter)),
        ).notatedReading()

        assertEquals(2, reading.size)
        assertNull(reading[1].note, "a rest has no pitch")
        assertEquals(NoteValue.QUARTER, reading[1].notated?.value)
        assertEquals(quarter, reading[1].duration)
    }

    @Test
    fun `a rest crossing a bar line becomes two rests, untied`() {
        // Three quarters, then three beats of silence running from beat 3 to beat 6.
        val reading = ToneSequence(
            steps = List(3) { ToneStep(note(it), quarter) } + ToneStep.rest(Beats(3.0)),
        ).notatedReading()

        val silence = reading.filter { it.note == null }
        assertEquals(2, silence.size, "cut at the bar line, like anything else")
        assertEquals(1.0, silence[0].duration.value, 1e-9)
        assertEquals(2.0, silence[1].duration.value, 1e-9)
        assertTrue(silence.none { it.tiedToNext }, "a silence is not held over a bar line")
        assertTrue(silence.none { it.continuesPrevious })
        assertTrue(silence.all { it.stepIndex == 3 }, "both halves are the same step")
    }

    @Test
    fun `a note crossing a bar line is still tied`() {
        // The rule the rests are the exception to, kept under test beside them.
        val reading = ToneSequence(
            steps = List(3) { ToneStep(note(it), quarter) } + ToneStep(note(4), Beats(3.0)),
        ).notatedReading()

        val held = reading.filter { it.stepIndex == 3 }
        assertEquals(2, held.size)
        assertTrue(held[0].tiedToNext)
        assertTrue(held[1].continuesPrevious)
        assertFalse(held[1].tiedToNext)
    }

    // --- rests and the beat ---------------------------------------------------------------------

    private val eighth = Beats(0.5)

    private fun restsOf(sequence: ToneSequence) = sequence.notatedReading().filter { it.note == null }

    @Test
    fun `a rest after the and of three is an eighth rest and a quarter rest`() {
        // Five eighths and a beat and a half of silence: the end of the scale run on the blues.
        val rests = restsOf(ToneSequence(steps = List(5) { ToneStep(note(it), eighth) } + ToneStep.rest(Beats(1.5))))

        assertEquals(listOf(NoteValue.EIGHTH, NoteValue.QUARTER), rests.map { it.notated?.value })
        assertEquals(listOf(0, 0), rests.map { it.notated?.dots })
        assertEquals(listOf(2.5, 3.0), rests.map { it.startBeat.value })
    }

    @Test
    fun `a rest from beat two over the middle of the bar is two quarter rests`() {
        val rests = restsOf(
            ToneSequence(steps = listOf(ToneStep(note(0), quarter), ToneStep.rest(Beats(2.0)), ToneStep(note(1), quarter))),
        )

        assertEquals(listOf(NoteValue.QUARTER, NoteValue.QUARTER), rests.map { it.notated?.value })
    }

    @Test
    fun `a rest from the middle of the bar to its end stays one half rest`() {
        val rests = restsOf(ToneSequence(steps = List(2) { ToneStep(note(it), quarter) } + ToneStep.rest(Beats(2.0))))

        assertEquals(listOf(NoteValue.HALF), rests.map { it.notated?.value })
    }

    @Test
    fun `a whole bar of rest stays one rest`() {
        val rests = restsOf(ToneSequence(steps = listOf(ToneStep.rest(Beats(4.0)))))

        assertEquals(1, rests.size)
        assertEquals(NoteValue.WHOLE, rests.single().notated?.value)
    }
}

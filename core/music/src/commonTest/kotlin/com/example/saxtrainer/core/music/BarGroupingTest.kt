package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a sequence is cut into bars.
 *
 * The grouping a page layout is built on, so what matters here is that it never splits a step and
 * never loses one: a row of music made of whole groups is then a row that cannot tear a note in
 * half at the end of a line.
 */
class BarGroupingTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER).beats()
    private val half = NotatedDuration(NoteValue.HALF).beats()

    private fun note(index: Int) = Note(NoteName.entries[index % 12], 4)

    /** Quarter notes, [count] of them: four to a bar in four-four. */
    private fun quarters(count: Int) = ToneSequence(
        steps = List(count) { ToneStep(note(it), quarter) },
    )

    @Test
    fun `four bars of quarters are four bars`() {
        assertEquals(listOf(0..3, 4..7, 8..11, 12..15), quarters(16).bars())
    }

    @Test
    fun `a single bar is one group`() {
        assertEquals(listOf(0..3), quarters(4).bars())
    }

    @Test
    fun `a part-filled last bar is still a bar`() {
        assertEquals(listOf(0..3, 4..5), quarters(6).bars())
    }

    @Test
    fun `a note running past a bar line keeps the bar it started in`() {
        // Five beats, then three quarters: nothing begins on the bar line at beat four, so there
        // is no bar to open there. The written notation ties the long note across the line; the
        // step it belongs to does not move.
        val sequence = ToneSequence(
            steps = listOf(ToneStep(note(0), Beats(5.0))) + List(3) { ToneStep(note(it), quarter) },
        )

        assertEquals(listOf(0..3), sequence.bars())
    }

    @Test
    fun `a rest opens a bar like anything else`() {
        val sequence = ToneSequence(
            steps = List(4) { ToneStep(note(it), quarter) } +
                ToneStep.rest(quarter) +
                List(3) { ToneStep(note(it), quarter) },
        )

        assertEquals(listOf(0..3, 4..7), sequence.bars())
    }

    @Test
    fun `a bar of longer notes is a bar all the same`() {
        val sequence = ToneSequence(steps = List(4) { ToneStep(note(it), half) })

        assertEquals(listOf(0..1, 2..3), sequence.bars())
    }

    @Test
    fun `three-four counts three beats to a bar`() {
        val sequence = ToneSequence(
            steps = List(6) { ToneStep(note(it), quarter) },
            timeSignature = TimeSignature(3, 4),
        )

        assertEquals(listOf(0..2, 3..5), sequence.bars())
    }

    @Test
    fun `every step belongs to exactly one bar, in order`() {
        val sequence = quarters(11)
        val grouped = sequence.bars().flatMap { it.toList() }

        assertEquals(sequence.steps.indices.toList(), grouped, "nothing lost, nothing repeated")
        assertTrue(sequence.bars().first().first == 0, "the first bar starts at the first step")
    }
}

package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which notes are beamed together.
 *
 * The rule under test is this engine's, not a general statement about engraving: within a bar,
 * consecutive flagged notes beginning in the same beat are joined. Compound metres and cross-beat
 * groups are out of scope, and these tests say so rather than pretending otherwise.
 */
class BeamGroupingTest {

    private fun note(step: Int) = Note(NoteName.entries[step % 12], 4)

    private fun sequenceOf(vararg durations: NotatedDuration) = ToneSequence(
        steps = durations.mapIndexed { index, duration -> ToneStep(note(index), duration.beats()) },
    )

    private val whole = NotatedDuration(NoteValue.WHOLE)
    private val half = NotatedDuration(NoteValue.HALF)
    private val quarter = NotatedDuration(NoteValue.QUARTER)
    private val dottedQuarter = NotatedDuration(NoteValue.QUARTER, dots = 1)
    private val eighth = NotatedDuration(NoteValue.EIGHTH)
    private val sixteenth = NotatedDuration(NoteValue.SIXTEENTH)

    @Test
    fun `quarters and longer are never beamed`() {
        assertEquals(emptyList<IntRange>(), sequenceOf(quarter, quarter, half, whole).beamGroups())
    }

    @Test
    fun `two eighths in one beat are beamed`() {
        assertEquals(listOf(0..1), sequenceOf(eighth, eighth, quarter).beamGroups())
    }

    @Test
    fun `a group does not run across a beat`() {
        // Four eighths fill beats 0 and 1, which is two groups of two rather than one of four.
        assertEquals(listOf(0..1, 2..3), sequenceOf(eighth, eighth, eighth, eighth).beamGroups())
    }

    @Test
    fun `a lone eighth keeps its flag`() {
        // The dotted quarter fills beat 0 and half of beat 1, so the eighth is alone in beat 1.
        val groups = sequenceOf(dottedQuarter, eighth, quarter, quarter).beamGroups()

        assertEquals(emptyList<IntRange>(), groups)
    }

    @Test
    fun `a quarter breaks a group`() {
        assertEquals(listOf(2..3), sequenceOf(quarter, quarter, eighth, eighth).beamGroups())
    }

    @Test
    fun `four sixteenths in one beat are one group`() {
        val groups = sequenceOf(sixteenth, sixteenth, sixteenth, sixteenth, quarter).beamGroups()

        assertEquals(listOf(0..3), groups)
        assertEquals(2, sequenceOf(sixteenth, sixteenth).beamCountOf(0))
    }

    @Test
    fun `a group that ends the sequence is still a group`() {
        assertEquals(listOf(1..2), sequenceOf(quarter, eighth, eighth).beamGroups())
    }

    @Test
    fun `groups never overlap and are in order`() {
        val groups = sequenceOf(
            eighth, eighth, eighth, eighth, quarter, sixteenth, sixteenth, sixteenth, sixteenth,
        ).beamGroups()

        groups.zipWithNext { earlier, later ->
            assertTrue(earlier.last < later.first, "$earlier then $later")
        }
    }

    @Test
    fun `a duration with no written form is never beamed`() {
        // A five-beat long tone: not a note value at all, so nothing to beam it to.
        val sequence = ToneSequence(
            steps = listOf(
                ToneStep(note(0), Beats(5.0)),
                ToneStep(note(1), Beats(5.0)),
            ),
        )

        assertEquals(emptyList<IntRange>(), sequence.beamGroups())
        assertEquals(0, sequence.beamCountOf(0))
        assertEquals(null, sequence.notatedDurationOf(0))
    }

    /** Eighths, some of them silent: a rest has no stem for a beam to reach. */
    private fun withRests(vararg beamed: Boolean) = ToneSequence(
        steps = beamed.mapIndexed { index, sounds ->
            if (sounds) ToneStep(note(index), eighth.beats()) else ToneStep.rest(eighth.beats())
        },
    )

    @Test
    fun `a rest breaks the beam it stands in`() {
        // Eighth, rest, eighth, eighth: the first is left alone in its beat and keeps its flag,
        // and only the pair in the second beat is joined.
        assertEquals(listOf(2..3), withRests(true, false, true, true).beamGroups())
    }

    @Test
    fun `two eighths either side of a rest are not one group`() {
        assertEquals(emptyList<IntRange>(), withRests(true, false).beamGroups())
    }

}

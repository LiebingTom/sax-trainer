package com.example.saxtrainer.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Making more practice out of one piece of material. */
class SequenceShapingTest {

    private fun note(name: NoteName) = Note(name, 4)

    private val quarter = Beats(1.0)
    private val half = Beats(2.0)

    private val upThree = ToneSequence(
        steps = listOf(
            ToneStep(note(NoteName.C), quarter),
            ToneStep(note(NoteName.D), quarter),
            ToneStep(note(NoteName.E), quarter),
        ),
        name = "Up three",
    )

    @Test
    fun `going back down does not play the turning point twice`() {
        val there = upThree.andBackDown()

        assertEquals(
            listOf("C4", "D4", "E4", "D4", "C4"),
            there.steps.map { it.soundedNote.label },
        )
    }

    @Test
    fun `a returning note keeps the length it had on the way up`() {
        val uneven = ToneSequence(
            steps = listOf(
                ToneStep(note(NoteName.C), half),
                ToneStep(note(NoteName.D), quarter),
                ToneStep(note(NoteName.E), quarter),
            ),
        )

        val there = uneven.andBackDown()

        assertEquals(listOf(half, quarter, quarter, quarter, half), there.steps.map { it.duration })
    }

    @Test
    fun `a single note has nowhere to come back from`() {
        val one = ToneSequence(steps = listOf(ToneStep(note(NoteName.C), quarter)))

        assertSame(one, one.andBackDown())
    }

    @Test
    fun `going back down keeps everything else about the music`() {
        val flats = upThree.copy(accidentalStyle = AccidentalStyle.FLATS)

        val there = flats.andBackDown()

        assertEquals(AccidentalStyle.FLATS, there.accidentalStyle)
        assertEquals(flats.timeSignature, there.timeSignature)
        assertEquals("Up three", there.name)
    }

    @Test
    fun `repeating writes the notes out again`() {
        val threeTimes = upThree.repeated(3)

        assertEquals(9, threeTimes.steps.size)
        assertEquals(upThree.steps, threeTimes.steps.take(3))
        assertEquals(upThree.steps, threeTimes.steps.takeLast(3))
        assertEquals(Beats(9.0), threeTimes.totalBeats)
    }

    @Test
    fun `beat positions carry on across a repeat`() {
        val twice = upThree.repeated(2)

        // The fourth note is the first of the second pass, three beats in.
        assertEquals(Beats(3.0), twice.startBeatOf(3))
    }

    @Test
    fun `playing it once changes nothing`() {
        assertSame(upThree, upThree.repeated(1))
    }

    @Test
    fun `a sequence is played at least once`() {
        assertFailsWith<IllegalArgumentException> { upThree.repeated(0) }
        assertFailsWith<IllegalArgumentException> { upThree.repeated(-1) }
    }

    @Test
    fun `the two shapes compose, back down first and then over again`() {
        val shaped = upThree.andBackDown().repeated(2)

        assertEquals(10, shaped.steps.size)
        assertEquals(
            listOf("C4", "D4", "E4", "D4", "C4", "C4", "D4", "E4", "D4", "C4"),
            shaped.steps.map { it.soundedNote.label },
        )
    }
}

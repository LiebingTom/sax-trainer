package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SequenceRunnerTest {

    private fun held(vararg notes: Note) = ToneSequence(notes.map { ToneStep(it, Beats.of(4)) })

    private val threeNotes = held(Note(NoteName.A, 4), Note(NoteName.B, 4), Note(NoteName.C, 5))

    private val singleNote = held(Note(NoteName.A, 4))

    @Test
    fun `a new runner sits on the first note`() {
        val runner = SequenceRunner(threeNotes)

        assertEquals(0, runner.currentIndex)
        assertEquals(threeNotes.steps.first(), runner.currentStep)
        assertFalse(runner.isFinished)
        assertFalse(runner.isLastStep)
    }

    @Test
    fun `advancing walks through every note`() {
        val runner = SequenceRunner(threeNotes)

        assertTrue(runner.advance())
        assertEquals(threeNotes.steps[1], runner.currentStep)
        assertTrue(runner.advance())
        assertEquals(threeNotes.steps[2], runner.currentStep)
        assertTrue(runner.isLastStep)
    }

    @Test
    fun `advancing past the last note finishes without running off the end`() {
        val runner = SequenceRunner(threeNotes)

        runner.advance()
        runner.advance()
        assertFalse(runner.advance(), "the last note has no successor")

        assertTrue(runner.isFinished)
        assertEquals(2, runner.currentIndex)
        assertEquals(threeNotes.steps.last(), runner.currentStep)

        // Repeated calls stay harmless.
        assertFalse(runner.advance())
        assertEquals(2, runner.currentIndex)
    }

    @Test
    fun `a single note sequence finishes on the first advance`() {
        val runner = SequenceRunner(singleNote)

        assertTrue(runner.isLastStep)
        assertFalse(runner.advance())
        assertTrue(runner.isFinished)
        assertEquals(singleNote.steps.single(), runner.currentStep)
    }

    @Test
    fun `upcoming shows what comes next at the start`() {
        val runner = SequenceRunner(threeNotes)

        assertEquals(threeNotes.steps.drop(1), runner.upcoming(5))
        assertEquals(listOf(threeNotes.steps[1]), runner.upcoming(1))
        assertEquals(emptyList<ToneStep>(), runner.upcoming(0))
    }

    @Test
    fun `upcoming shrinks towards the end and is empty when finished`() {
        val runner = SequenceRunner(threeNotes)

        runner.advance()
        assertEquals(listOf(threeNotes.steps[2]), runner.upcoming(5))

        runner.advance()
        assertTrue(runner.upcoming(5).isEmpty(), "nothing follows the last note")

        runner.advance()
        assertTrue(runner.upcoming(5).isEmpty())
    }

    @Test
    fun `progress counts notes and beats`() {
        val runner = SequenceRunner(threeNotes)

        runner.progress.let {
            assertEquals(0, it.stepIndex)
            assertEquals(1, it.displayPosition)
            assertEquals(3, it.stepCount)
            assertEquals(0.0, it.completedBeats.value, 1e-9)
            assertEquals(12.0, it.totalBeats.value, 1e-9)
            assertEquals(0f, it.fraction, 1e-6f)
        }

        runner.advance()
        runner.progress.let {
            assertEquals(2, it.displayPosition)
            assertEquals(4.0, it.completedBeats.value, 1e-9)
            assertEquals(1f / 3f, it.fraction, 1e-6f)
        }
    }

    @Test
    fun `a finished sequence reports full progress`() {
        val runner = SequenceRunner(threeNotes)
        repeat(3) { runner.advance() }

        assertEquals(12.0, runner.progress.completedBeats.value, 1e-9)
        assertEquals(1f, runner.progress.fraction, 1e-6f)
    }

    @Test
    fun `reset starts the exercise over`() {
        val runner = SequenceRunner(threeNotes)
        repeat(3) { runner.advance() }

        runner.reset()

        assertEquals(0, runner.currentIndex)
        assertFalse(runner.isFinished)
        assertEquals(threeNotes.steps.first(), runner.currentStep)
    }

    @Test
    fun `the runner holds no timing of its own`() {
        // Nothing advances without an explicit call: the feature owns the clock.
        val runner = SequenceRunner(
            ToneSequence(List(4) { ToneStep(Note(NoteName.A, 4), Beats.of(4)) }),
        )

        assertEquals(0, runner.currentIndex)
        assertEquals(0.0, runner.progress.completedBeats.value, 1e-9)
    }
}

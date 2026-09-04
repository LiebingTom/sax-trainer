package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTimingTest {

    private val tolerance = TimingTolerance(onTimeSeconds = 0.1, windowSeconds = 0.4)

    @Test
    fun `close enough to the beat is on it`() {
        assertEquals(NoteTiming.ON_TIME, tolerance.judge(expectedSeconds = 2.0, onsetSeconds = 2.0))
        assertEquals(NoteTiming.ON_TIME, tolerance.judge(2.0, 2.09))
        assertEquals(NoteTiming.ON_TIME, tolerance.judge(2.0, 1.91))
    }

    @Test
    fun `the edge of the window still counts`() {
        assertEquals(NoteTiming.ON_TIME, tolerance.judge(2.0, 2.1))
        assertEquals(NoteTiming.ON_TIME, tolerance.judge(2.0, 1.9))
    }

    @Test
    fun `early and late are told apart`() {
        assertEquals(NoteTiming.LATE, tolerance.judge(2.0, 2.3))
        assertEquals(NoteTiming.EARLY, tolerance.judge(2.0, 1.7))
    }

    @Test
    fun `nothing heard is not the same as being late`() {
        assertEquals(NoteTiming.NOT_PLAYED, tolerance.judge(2.0, onsetSeconds = null))
    }

    @Test
    fun `a sound too far away belongs to another note`() {
        assertTrue(tolerance.belongsToNote(expectedSeconds = 2.0, onsetSeconds = 2.35))
        assertTrue(!tolerance.belongsToNote(expectedSeconds = 2.0, onsetSeconds = 2.6))
    }

    @Test
    fun `a tolerance has to make sense`() {
        assertThrows(IllegalArgumentException::class.java) { TimingTolerance(onTimeSeconds = 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            TimingTolerance(onTimeSeconds = 0.5, windowSeconds = 0.1)
        }
    }

    // --- musical time becomes clock time, and only here ------------------------------------------

    private val sequence = ToneSequence(
        steps = listOf(
            ToneStep(Note(NoteName.C, 4), Beats(1.0)),
            ToneStep(Note(NoteName.D, 4), Beats(1.0)),
            ToneStep(Note(NoteName.E, 4), Beats(2.0)),
        ),
    )

    @Test
    fun `when a note is due comes from the tempo, not from the sequence`() {
        assertEquals(0.0, sequence.expectedSecondsOf(0, Tempo(60)), 1e-9)
        assertEquals(1.0, sequence.expectedSecondsOf(1, Tempo(60)), 1e-9)
        assertEquals(2.0, sequence.expectedSecondsOf(2, Tempo(60)), 1e-9)

        // The same music, twice as fast.
        assertEquals(0.5, sequence.expectedSecondsOf(1, Tempo(120)), 1e-9)
        assertEquals(1.0, sequence.expectedSecondsOf(2, Tempo(120)), 1e-9)
    }

    @Test
    fun `a note ends where its duration takes it`() {
        assertEquals(Beats(1.0), sequence.endBeatOf(0))
        assertEquals(Beats(4.0), sequence.endBeatOf(2))
        assertEquals(4.0, sequence.totalSeconds(Tempo(60)), 1e-9)
        assertEquals(2.0, sequence.totalSeconds(Tempo(120)), 1e-9)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonotonicBeatClockTest {

    private fun TestScope.clock() = MonotonicBeatClock(
        tickIntervalMs = 50,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    @Test
    fun `the clock starts at nothing`() = runTest {
        assertEquals(Beats.ZERO, clock().elapsedBeats(Tempo(60)).take(1).toList().single())
    }

    @Test
    fun `a second is a beat at sixty`() = runTest {
        val beats = clock().elapsedBeats(Tempo(60)).take(21).toList()

        // Twenty ticks of fifty milliseconds is one second, which at sixty is one beat.
        assertEquals(1.0, beats.last().value, 1e-6)
    }

    @Test
    fun `the same time is two beats at a hundred and twenty`() = runTest {
        val beats = clock().elapsedBeats(Tempo(120)).take(21).toList()

        assertEquals(2.0, beats.last().value, 1e-6)
    }

    @Test
    fun `position is read from the source, never accumulated`() = runTest {
        // Every emission is the true elapsed time, so no tick can drift the exercise.
        val beats = clock().elapsedBeats(Tempo(60)).take(11).toList()

        beats.forEachIndexed { index, value ->
            assertEquals(index * 0.05, value.value, 1e-6)
        }
    }
}

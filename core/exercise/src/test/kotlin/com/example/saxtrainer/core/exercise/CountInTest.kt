package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counting a practice in. */
@OptIn(ExperimentalCoroutinesApi::class)
class CountInTest {

    private fun TestScope.clock() = MonotonicBeatClock(
        tickIntervalMs = 25,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    @Test
    fun `four beats are counted four, three, two, one`() = runTest {
        val counted = mutableListOf<Int>()

        clock().countIn(beats = 4, tempo = Tempo(60), onCount = counted::add)

        assertEquals(listOf(4, 3, 2, 1), counted)
    }

    @Test
    fun `the count ends when the last beat has passed`() = runTest {
        clock().countIn(beats = 4, tempo = Tempo(60)) {}

        // Four beats at sixty is four seconds; it returns on the beat, not a tick early.
        assertTrue("returned after $currentTime ms", currentTime >= 4_000)
        assertTrue("returned after $currentTime ms", currentTime < 4_000 + 25)
    }

    @Test
    fun `the tempo decides how long a count takes, not how many numbers it has`() = runTest {
        val counted = mutableListOf<Int>()

        clock().countIn(beats = 4, tempo = Tempo(120), onCount = counted::add)

        assertEquals(listOf(4, 3, 2, 1), counted)
        assertTrue("took $currentTime ms", currentTime in 2_000..2_024)
    }

    @Test
    fun `no count-in returns without counting anything`() = runTest {
        val counted = mutableListOf<Int>()

        clock().countIn(beats = 0, tempo = Tempo(60), onCount = counted::add)

        assertEquals(emptyList<Int>(), counted)
        assertEquals(0, currentTime)
    }

    @Test
    fun `a paused count stands still and picks up where it left off`() = runTest {
        val switch = PauseSwitch()
        val counted = mutableListOf<Int>()
        val start = currentTime

        val job = launch {
            clock().pausedBy(switch).countIn(beats = 4, tempo = Tempo(60), onCount = counted::add)
        }

        advanceTimeBy(1_100)
        switch.pause()
        advanceTimeBy(10_000)

        assertEquals("the count moved on while it was held", listOf(4, 3), counted)

        switch.resume()
        job.join()

        assertEquals(listOf(4, 3, 2, 1), counted)
        // Four beats of counting plus ten seconds of being held, and nothing else.
        assertTrue("took ${currentTime - start} ms", currentTime - start >= 14_000)
    }

    @Test
    fun `the lead-in is half the count`() {
        assertEquals(2, leadInBeatsFor(4))
        assertEquals(1, leadInBeatsFor(2))
        assertEquals(4, leadInBeatsFor(8))
        // Nothing to be half of, and a practice that starts on the button needs no getting ready.
        assertEquals(0, leadInBeatsFor(0))
    }

    @Test
    fun `the lead-in waits its beats out silently`() = runTest {
        clock().leadIn(beats = 2, tempo = Tempo(60))

        assertTrue("returned after $currentTime ms", currentTime in 2_000..2_024)
    }

    @Test
    fun `no lead-in takes no time`() = runTest {
        clock().leadIn(beats = 0, tempo = Tempo(60))

        assertEquals(0, currentTime)
    }

    @Test
    fun `the lead-in is at the tempo, so it is shorter when the tempo is faster`() = runTest {
        clock().leadIn(beats = 2, tempo = Tempo(120))

        assertTrue("returned after $currentTime ms", currentTime in 1_000..1_024)
    }
}

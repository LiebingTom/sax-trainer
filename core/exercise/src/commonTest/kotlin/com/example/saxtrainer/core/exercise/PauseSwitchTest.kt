package com.example.saxtrainer.core.exercise

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Holding an exercise without ending it. */
@OptIn(ExperimentalCoroutinesApi::class)
class PauseSwitchTest {

    /** A clock that emits one tick per second, whatever anyone does with it. */
    private fun seconds(count: Int, onEach: (Int) -> Unit = {}) = flow {
        for (second in 0 until count) {
            onEach(second)
            emit(second.toFloat())
        }
    }

    @Test
    fun `a new switch is not paused`() {
        assertFalse(PauseSwitch().paused)
    }

    @Test
    fun `toggling answers where it landed`() {
        val switch = PauseSwitch()

        assertTrue(switch.toggle())
        assertTrue(switch.isPaused.value)
        assertFalse(switch.toggle())
        assertFalse(switch.isPaused.value)
    }

    @Test
    fun `an unpaused clock passes straight through`() = runTest {
        val positions = seconds(4).discountingPausedSeconds(PauseSwitch()).toList()

        assertEquals(listOf(0f, 1f, 2f, 3f), positions)
    }

    @Test
    fun `nothing is emitted while the exercise is held`() = runTest {
        val switch = PauseSwitch()
        // Held from the second tick to the fifth: three seconds of wall time, no exercise time.
        val positions = seconds(8) { second ->
            when (second) {
                2 -> switch.pause()
                5 -> switch.resume()
            }
        }.discountingPausedSeconds(switch).toList()

        // Eight ticks went by, three of them while it was held, and only five reached the exercise.
        assertEquals(5, positions.size)
    }

    @Test
    fun `time spent paused is discounted, so the exercise carries on where it stood`() = runTest {
        val switch = PauseSwitch()
        val positions = seconds(8) { second ->
            when (second) {
                2 -> switch.pause()
                5 -> switch.resume()
            }
        }.discountingPausedSeconds(switch).toList()

        // Two seconds were played, three were held, and the exercise is at two seconds again.
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), positions)
    }

    @Test
    fun `two pauses are both discounted`() = runTest {
        val switch = PauseSwitch()
        val positions = seconds(10) { second ->
            when (second) {
                1 -> switch.pause()
                3 -> switch.resume()
                5 -> switch.pause()
                8 -> switch.resume()
            }
        }.discountingPausedSeconds(switch).toList()

        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), positions)
    }
}

package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Counting a practice in on the click.
 *
 * The contract is what makes both metronomes interchangeable: the count follows the beats the
 * engine *publishes*, and an engine publishes a beat only once it has been heard. Android knows
 * that from the playback head, the browser from the audio context's clock; neither is visible here,
 * which is the point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClickCountInTest {

    @Test
    fun `the count follows the clicks and returns on the beat after the last`() = runTest {
        val metronome = FakeMetronome()
        val counted = mutableListOf<Int>()
        var returned = false

        val counting = launch {
            metronome.clickCountIn(beats = 4, tempo = Tempo(90)) { counted += it }
            returned = true
        }
        runCurrent()

        metronome.beat()
        runCurrent()
        assertEquals(listOf(4), counted, "the first count did not wait for the first click")

        repeat(3) {
            metronome.beat()
            runCurrent()
        }
        assertEquals(listOf(4, 3, 2, 1), counted)
        assertFalse(returned, "returned before the fourth click had sounded")

        // The beat the exercise begins on: the one after the last counted click.
        metronome.beat()
        counting.join()
        assertTrue(returned)
    }

    @Test
    fun `the count-in sets the tempo and starts the click`() = runTest {
        val metronome = FakeMetronome()

        val counting = launch { metronome.clickCountIn(beats = 1, tempo = Tempo(132)) {} }
        runCurrent()
        repeat(2) {
            metronome.beat()
            runCurrent()
        }
        counting.join()

        assertEquals(132, metronome.state.value.bpm)
        assertTrue(metronome.started, "the count-in never started the click")
    }

    @Test
    fun `the click is left running for whatever comes next`() = runTest {
        val metronome = FakeMetronome()

        val counting = launch { metronome.clickCountIn(beats = 2, tempo = Tempo(90)) {} }
        runCurrent()
        repeat(3) {
            metronome.beat()
            runCurrent()
        }
        counting.join()

        assertFalse(metronome.stopped, "the count-in stopped a click its caller may still need")
    }

    @Test
    fun `no count-in at all is not a count-in of one`() = runTest {
        val metronome = FakeMetronome()

        metronome.clickCountIn(beats = 0, tempo = Tempo(90)) { fail() }

        assertFalse(metronome.started, "a count-in of no beats still started the click")
    }

    private fun fail(): Nothing = throw AssertionError("counted a beat that was never asked for")

    /**
     * A metronome whose beats are published by hand, so the count-in can be driven a beat at a time
     * without any real time passing.
     */
    private class FakeMetronome : Metronome {
        private val _state = MutableStateFlow(MetronomeEngineState(beatsPerBar = 4))
        override val state: StateFlow<MetronomeEngineState> = _state.asStateFlow()

        var started = false
            private set

        var stopped = false
            private set

        private var beatInBar = 0

        override fun setBpm(bpm: Int) {
            _state.value = _state.value.copy(bpm = bpm)
        }

        override fun setTimeSignature(signature: TimeSignature) {
            _state.value = _state.value.copy(beatsPerBar = signature.beatsPerBar)
        }

        override fun setAccentFirstBeat(enabled: Boolean) = Unit

        override fun setHalfBeatSound(enabled: Boolean) = Unit

        override fun start() {
            started = true
            _state.value = _state.value.copy(isRunning = true)
        }

        override fun stop() {
            stopped = true
            _state.value = _state.value.copy(isRunning = false)
        }

        override fun release() = stop()

        /** Publishes one beat, as an engine does once the player has heard it. */
        fun beat() {
            _state.value = _state.value.copy(
                beatInBar = beatInBar,
                beatCounter = _state.value.beatCounter + 1,
            )
            beatInBar = (beatInBar + 1) % 4
        }
    }
}

package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The open metronome, as something outside its screen sees and toggles it. */
class ActiveMetronomeTest {

    @Test
    fun `nothing is open until a metronome is attached, and nothing after it is detached`() = runTest {
        val active = ActiveMetronome()
        val metronome = BeatingMetronome()
        assertNull(active.currentSummary)

        active.attach(metronome)
        assertEquals(ActiveMetronome.Summary(isRunning = false, bpm = 80, beatsPerBar = 4), active.currentSummary)

        active.detach(metronome)
        assertNull(active.currentSummary)
        assertNull(active.summary.first())
    }

    @Test
    fun `toggle starts a stopped metronome and stops a running one`() {
        val active = ActiveMetronome()
        val metronome = BeatingMetronome()
        active.attach(metronome)

        active.toggle()
        assertTrue(metronome.state.value.isRunning)
        assertTrue(active.isRunning)

        active.toggle()
        assertFalse(metronome.state.value.isRunning)
    }

    @Test
    fun `with nothing open, toggle and stop do nothing`() {
        val active = ActiveMetronome()
        active.toggle()
        active.stop()
        assertFalse(active.isRunning)
    }

    @Test
    fun `a screen going does not detach the one that came after it`() {
        val active = ActiveMetronome()
        val first = BeatingMetronome()
        val second = BeatingMetronome()
        active.attach(first)
        active.attach(second)

        active.detach(first)

        second.setBpm(120)
        assertEquals(120, active.currentSummary?.bpm)
    }

    @Test
    fun `the summary changes with tempo and running, not with every beat`() = runTest(UnconfinedTestDispatcher()) {
        val active = ActiveMetronome()
        val metronome = BeatingMetronome()
        active.attach(metronome)

        val seen = mutableListOf<ActiveMetronome.Summary?>()
        val collecting = launch { active.summary.take(3).toList(seen) }
        metronome.start()
        repeat(5) { metronome.beat() }
        metronome.setBpm(100)
        collecting.join()

        assertEquals(
            listOf<ActiveMetronome.Summary?>(
                ActiveMetronome.Summary(false, 80, 4),
                ActiveMetronome.Summary(true, 80, 4),
                ActiveMetronome.Summary(true, 100, 4),
            ),
            seen,
        )
    }

    /** A metronome whose beats are published by hand. */
    private class BeatingMetronome : Metronome {
        private val _state = MutableStateFlow(MetronomeEngineState(beatsPerBar = 4))
        override val state: StateFlow<MetronomeEngineState> = _state.asStateFlow()

        override fun setBpm(bpm: Int) {
            _state.value = _state.value.copy(bpm = bpm)
        }

        override fun setTimeSignature(signature: TimeSignature) {
            _state.value = _state.value.copy(beatsPerBar = signature.beatsPerBar)
        }

        override fun setAccentFirstBeat(enabled: Boolean) = Unit

        override fun setHalfBeatSound(enabled: Boolean) = Unit

        override fun start() {
            _state.value = _state.value.copy(isRunning = true)
        }

        override fun stop() {
            _state.value = _state.value.copy(isRunning = false)
        }

        override fun release() = stop()

        fun beat() {
            val s = _state.value
            _state.value = s.copy(beatCounter = s.beatCounter + 1, beatInBar = (s.beatInBar + 1) % s.beatsPerBar)
        }
    }
}

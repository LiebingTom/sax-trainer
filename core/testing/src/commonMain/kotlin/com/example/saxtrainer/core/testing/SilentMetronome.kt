package com.example.saxtrainer.core.testing

import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.MetronomeEngineState
import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A metronome that makes no sound and counts no beats.
 *
 * Every trainer takes a metronome factory whether or not it clicks, and a test for something else
 * still has to hand it one. Its state follows what it is told, so a screen that shows whether the
 * click is running still shows the right thing; nothing here produces beats, which is what
 * `ClickCountInTest`'s own fake is for.
 */
class SilentMetronome : Metronome {
    private val _state = MutableStateFlow(MetronomeEngineState())
    override val state: StateFlow<MetronomeEngineState> = _state.asStateFlow()

    override fun setBpm(bpm: Int) {
        _state.value = _state.value.copy(bpm = bpm)
    }

    override fun setTimeSignature(signature: TimeSignature) {
        _state.value = _state.value.copy(beatsPerBar = signature.beatsPerBar)
    }

    // No sound to accent either way, so there is nothing to keep.
    override fun setAccentFirstBeat(enabled: Boolean) = Unit

    override fun setHalfBeatSound(enabled: Boolean) = Unit

    override fun start() {
        _state.value = _state.value.copy(isRunning = true)
    }

    override fun stop() {
        _state.value = _state.value.copy(isRunning = false)
    }

    override fun release() = stop()
}

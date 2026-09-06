package com.example.saxtrainer.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.exercise.monotonicNanos
import com.example.saxtrainer.core.audio.metronome.MetronomeEngineState
import com.example.saxtrainer.core.audio.metronome.MetronomeError
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature

/** Everything the metronome screen renders. */
data class MetronomeUiState(
    val bpm: Int = Metronome.DEFAULT_BPM,
    val isRunning: Boolean = false,
    val beatsPerBar: Int = TimeSignature.FOUR_FOUR.beatsPerBar,
    val currentBeat: Int = 0,
    val beatCounter: Long = 0,
    val error: MetronomeError? = null,
) {
    val minBpm: Int get() = Tempo.SUPPORTED_RANGE.first
    val maxBpm: Int get() = Tempo.SUPPORTED_RANGE.last
}

/**
 * Owns the metronome engine and the tap-tempo estimator. Contains no timing logic itself - the
 * engine keeps the beat, this class only forwards user intent and exposes state.
 */
class MetronomeViewModel(
    private val engine: Metronome,
    /**
     * What the metronome was last started at. Defaulted so a metronome without one still runs; it
     * simply opens at the standard tempo every time.
     */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val tapTempo: TapTempo = TapTempo(),
    private val nanoTime: () -> Long = monotonicNanos,
) : ViewModel() {

    init {
        // Before the state flow below reads the engine, so the screen opens on the remembered
        // tempo rather than on the default and then a correction.
        val remembered = exerciseSetups.setupOf(Exercise.METRONOME)
        engine.setBpm(remembered.rememberedBpm(engine.state.value.bpm))
    }

    val uiState: StateFlow<MetronomeUiState> = engine.state
        .map(::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
            initialValue = toUiState(engine.state.value),
        )

    fun toggleRunning() {
        if (engine.state.value.isRunning) {
            engine.stop()
        } else {
            // The tempo it was started at becomes the tempo it opens at next time.
            exerciseSetups.remember(Exercise.METRONOME, metronomeSetup(engine.state.value.bpm))
            engine.start()
        }
    }

    /** Called when the screen is no longer visible; practising should not continue in the background. */
    fun pause() {
        engine.stop()
    }

    fun setBpm(bpm: Int) {
        engine.setBpm(bpm)
    }

    fun nudgeBpm(delta: Int) {
        engine.setBpm(engine.state.value.bpm + delta)
    }

    fun tap() {
        tapTempo.tap(nanoTime())?.let(engine::setBpm)
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    private fun toUiState(state: MetronomeEngineState) = MetronomeUiState(
        bpm = state.bpm,
        isRunning = state.isRunning,
        beatsPerBar = state.beatsPerBar,
        currentBeat = state.beatInBar,
        beatCounter = state.beatCounter,
        error = state.error,
    )

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}

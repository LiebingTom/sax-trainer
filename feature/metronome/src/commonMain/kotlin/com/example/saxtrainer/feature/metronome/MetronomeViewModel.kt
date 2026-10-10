package com.example.saxtrainer.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.metronome.ActiveMetronome
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
    /** Whether the midpoint after [currentBeat] has just been heard. */
    val halfBeatActive: Boolean = false,
    val beatCounter: Long = 0,
    val error: MetronomeError? = null,
    /** Whether the midpoint between beats gets its own marker - the settings screen's choice. */
    val visualizeHalfBeats: Boolean = true,
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
    /**
     * Where this screen's click is shown outside it - on Android, the notification that keeps it
     * playing with the phone locked. Attached for exactly as long as this screen exists.
     */
    private val active: ActiveMetronome? = null,
) : ViewModel() {

    init {
        active?.attach(engine)
        // Before the state flow below reads the engine, so the screen opens on the remembered
        // tempo, time signature and accent rather than on the defaults and then a correction. The
        // latter two are the settings screen's own choices; this only carries them into the click.
        val remembered = exerciseSetups.setupOf(Exercise.METRONOME)
        engine.setBpm(remembered.rememberedBpm(engine.state.value.bpm))
        engine.setTimeSignature(remembered.rememberedTimeSignature(TimeSignature.FOUR_FOUR))
        engine.setAccentFirstBeat(remembered.rememberedAccentFirstBeat())
        engine.setHalfBeatSound(remembered.rememberedHalfBeatSound())
    }

    // Display-only: the engine has no notion of how its beats are drawn, so this is read straight
    // into the UI state rather than passed through a setter as the tempo and time signature are.
    private val visualizeHalfBeats: Boolean =
        exerciseSetups.setupOf(Exercise.METRONOME).rememberedVisualizeHalfBeats()

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
            // The tempo it was started at becomes the tempo it opens at next time. The rest are
            // read fresh rather than kept from init, so a choice made on the settings screen since
            // this screen opened is not overwritten by a stale copy of it.
            val setup = exerciseSetups.setupOf(Exercise.METRONOME)
            exerciseSetups.remember(
                Exercise.METRONOME,
                metronomeSetup(
                    bpm = engine.state.value.bpm,
                    timeSignature = setup.rememberedTimeSignature(TimeSignature.FOUR_FOUR),
                    accentFirstBeat = setup.rememberedAccentFirstBeat(),
                    visualizeHalfBeats = setup.rememberedVisualizeHalfBeats(),
                    halfBeatSound = setup.rememberedHalfBeatSound(),
                ),
            )
            engine.start()
        }
    }

    /** Stops the click: when the screen is left, or hidden where it may not play on in the background. */
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
        // Stopped and let go of before it is released, so nothing outside this screen is left
        // showing - or able to start - a click that is going away.
        engine.stop()
        active?.detach(engine)
        engine.release()
        super.onCleared()
    }

    private fun toUiState(state: MetronomeEngineState) = MetronomeUiState(
        bpm = state.bpm,
        isRunning = state.isRunning,
        beatsPerBar = state.beatsPerBar,
        currentBeat = state.beatInBar,
        halfBeatActive = state.halfBeatActive,
        beatCounter = state.beatCounter,
        error = state.error,
        visualizeHalfBeats = visualizeHalfBeats,
    )

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}

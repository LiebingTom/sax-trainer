package com.example.saxtrainer.feature.metronome

import androidx.lifecycle.ViewModel
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the metronome's settings between opening this screen and starting the metronome.
 *
 * Written down as it is chosen rather than only when the metronome starts: the screen is left by
 * going back, which throws this ViewModel away, so anything not written down by then is lost - the
 * same reasoning every other setup screen in this app follows.
 */
class MetronomeSetupViewModel(
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        exerciseSetups.setupOf(Exercise.METRONOME).let { setup ->
            MetronomeSetupUiState(
                timeSignature = setup.rememberedTimeSignature(TimeSignature.FOUR_FOUR),
                accentFirstBeat = setup.rememberedAccentFirstBeat(),
                visualizeHalfBeats = setup.rememberedVisualizeHalfBeats(),
                halfBeatSound = setup.rememberedHalfBeatSound(),
            )
        },
    )
    val uiState: StateFlow<MetronomeSetupUiState> = _uiState.asStateFlow()

    fun selectTimeSignature(timeSignature: TimeSignature) {
        _uiState.value = _uiState.value.copy(timeSignature = timeSignature)
        writeSetup()
    }

    fun setAccentFirstBeat(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(accentFirstBeat = enabled)
        writeSetup()
    }

    fun setVisualizeHalfBeats(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(visualizeHalfBeats = enabled)
        writeSetup()
    }

    fun setHalfBeatSound(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(halfBeatSound = enabled)
        writeSetup()
    }

    /**
     * Writes the whole setup down from the state just changed, reading the tempo fresh rather than
     * from cached state: this ViewModel outlives a visit to the practice screen and back, so a
     * tempo remembered there since must not be overwritten by a stale copy of it.
     */
    private fun writeSetup() {
        val bpm = exerciseSetups.setupOf(Exercise.METRONOME).rememberedBpm(Metronome.DEFAULT_BPM)
        val state = _uiState.value
        exerciseSetups.remember(
            Exercise.METRONOME,
            metronomeSetup(
                bpm = bpm,
                timeSignature = state.timeSignature,
                accentFirstBeat = state.accentFirstBeat,
                visualizeHalfBeats = state.visualizeHalfBeats,
                halfBeatSound = state.halfBeatSound,
            ),
        )
    }
}

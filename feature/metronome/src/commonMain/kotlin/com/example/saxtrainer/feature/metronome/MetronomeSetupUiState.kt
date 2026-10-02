package com.example.saxtrainer.feature.metronome

import com.example.saxtrainer.core.music.TimeSignature

/** Everything the metronome settings screen renders. */
data class MetronomeSetupUiState(
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val accentFirstBeat: Boolean = true,
    val visualizeHalfBeats: Boolean = true,
    val halfBeatSound: Boolean = true,
)

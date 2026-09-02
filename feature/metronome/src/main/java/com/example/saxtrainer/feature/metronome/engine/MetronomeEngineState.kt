package com.example.saxtrainer.feature.metronome.engine

/** Why the metronome could not produce sound. */
enum class MetronomeError {
    AUDIO_UNAVAILABLE,
}

/**
 * What the engine reports to observers. [beatCounter] increases monotonically and exists so the UI
 * can react to every beat even when [beatInBar] repeats.
 */
data class MetronomeEngineState(
    val isRunning: Boolean = false,
    val bpm: Int = MetronomeEngine.DEFAULT_BPM,
    val beatsPerBar: Int = MetronomeEngine.BEATS_PER_BAR,
    val beatInBar: Int = 0,
    val beatCounter: Long = 0,
    val error: MetronomeError? = null,
)

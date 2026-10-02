package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.flow.StateFlow

/**
 * A click that keeps time, and says which beat it has actually played.
 *
 * The contract that matters is the last part: a beat appears in [state] once it has been *heard*,
 * not when it was scheduled. Everything that has to line up with the click - `clickCountIn`, a
 * sequence exercise - waits on that, which is why it can be an interface at all. How the beat is
 * produced is a platform's business: a render thread writing PCM, or clicks scheduled ahead against
 * an audio context's clock.
 */
interface Metronome {
    val state: StateFlow<MetronomeEngineState>

    /** Clamped to [com.example.saxtrainer.core.music.Tempo.SUPPORTED_RANGE]. */
    fun setBpm(bpm: Int)

    /** Takes effect from the bar that has not started yet, the same way a tempo change does. */
    fun setTimeSignature(signature: TimeSignature)

    /** Whether beat one of the bar gets the accent click rather than the normal one. On by default. */
    fun setAccentFirstBeat(enabled: Boolean)

    /**
     * Whether the midpoint between one beat and the next is sounded with its own, softer click.
     * On by default. The midpoint is tracked either way - see [MetronomeEngineState.halfBeatActive]
     * - this only says whether it is heard.
     */
    fun setHalfBeatSound(enabled: Boolean)

    fun start()

    fun stop()

    /** Gives up whatever the click is holding. It can be started again afterwards. */
    fun release()

    companion object {
        /** Where the metronome starts, and what a state with no tempo yet reports. */
        const val DEFAULT_BPM = 80
    }
}

/**
 * Where a screen gets a metronome from.
 *
 * Each practice that clicks owns one for as long as it runs, so this hands out a new one rather
 * than sharing a single instance - two exercises must never fight over one click.
 */
fun interface MetronomeFactory {
    fun create(): Metronome
}

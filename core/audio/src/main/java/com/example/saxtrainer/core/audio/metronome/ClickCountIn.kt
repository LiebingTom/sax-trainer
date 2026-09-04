package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.flow.first

/**
 * Counts a practice in out loud, on the metronome's own click.
 *
 * The count is driven by the beats the engine publishes rather than by a clock of its own, because
 * the engine publishes a beat only once the playback head has passed it: that is what puts the first
 * note on a click instead of a fraction of a second ahead of one. A silent count-in has nothing to
 * lock onto and uses `BeatClock.countIn` in `core:exercise` instead.
 *
 * The engine is started but never stopped here - a practice that clicks all the way through keeps it
 * running, and one that only counts in stops it itself.
 *
 * [onCount] is called with the number still to come, so a screen can show 4, 3, 2, 1. Returns as the
 * beat after the last one sounds, which is the beat the exercise begins on.
 */
suspend fun MetronomeEngine.clickCountIn(beats: Int, tempo: Tempo, onCount: (Int) -> Unit) {
    if (beats <= 0) return

    setBpm(tempo.bpm)
    val before = state.value.beatCounter
    start()

    val first = state.first { it.isRunning && it.beatCounter > before }

    for (beat in beats downTo 1) {
        onCount(beat)
        state.first { it.beatCounter >= first.beatCounter + (beats - beat) + 1 }
    }
}

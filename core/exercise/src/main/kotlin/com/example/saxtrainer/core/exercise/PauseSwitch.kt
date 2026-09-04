package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Whether an exercise is paused, and nothing else.
 *
 * Deliberately not a clock: it holds no time and knows no tempo, so it can gate anything an exercise
 * runs on - a clock, or a stream of audio frames. Pausing is not stopping: no result is published
 * and the run is still there to come back to.
 */
class PauseSwitch {

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    val paused: Boolean get() = _isPaused.value

    /** Flips the switch and answers where it landed. */
    fun toggle(): Boolean {
        _isPaused.value = !_isPaused.value
        return _isPaused.value
    }

    fun pause() {
        _isPaused.value = true
    }

    fun resume() {
        _isPaused.value = false
    }
}

/**
 * The same positions with the paused stretches taken out.
 *
 * A monotonic clock keeps running while the exercise is held, so pausing has to be subtracted rather
 * than hoped for: while [switch] is paused nothing is emitted, and once it is released the time that
 * passed in between is discounted, so the exercise carries on from where it stood instead of jumping
 * forward. The measurement runs from the first tick after the pause to the first tick after the
 * release, which makes it up to one tick generous at each end - tens of milliseconds, on a clock the
 * player is holding by hand.
 *
 * An exercise driven by audio rather than by a clock has nothing to discount: it pauses by not
 * analysing what it hears, and its own time then genuinely stands still.
 */
fun Flow<Float>.discountingPausedSeconds(switch: PauseSwitch): Flow<Float> =
    discounting(switch, position = Float::toDouble, rebuild = Double::toFloat)

/** [discountingPausedSeconds] for a clock that counts in beats. */
fun Flow<Beats>.discountingPausedBeats(switch: PauseSwitch): Flow<Beats> =
    discounting(switch, position = Beats::value, rebuild = ::Beats)

/** The same clock, standing still whenever [switch] is paused. */
fun BeatClock.pausedBy(switch: PauseSwitch): BeatClock = object : BeatClock {
    override fun elapsedBeats(tempo: Tempo): Flow<Beats> =
        this@pausedBy.elapsedBeats(tempo).discountingPausedBeats(switch)
}

private fun <T> Flow<T>.discounting(
    switch: PauseSwitch,
    position: (T) -> Double,
    rebuild: (Double) -> T,
): Flow<T> = flow {
    var pausedTotal = 0.0
    var pausedSince: Double? = null

    collect { tick ->
        val raw = position(tick)
        if (switch.paused) {
            if (pausedSince == null) pausedSince = raw
        } else {
            pausedSince?.let { since ->
                pausedTotal += raw - since
                pausedSince = null
            }
            emit(rebuild(raw - pausedTotal))
        }
    }
}

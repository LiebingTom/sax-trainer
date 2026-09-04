package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Where an exercise gets its musical position from. */
interface BeatClock {
    /** Beats elapsed since collection started, at [tempo]. */
    fun elapsedBeats(tempo: Tempo): Flow<Beats>
}

/**
 * A beat clock read from a monotonic source.
 *
 * The emitted position is computed from the source on every tick rather than accumulated from the
 * sleeps, so a late tick shortens the next interval instead of stretching the exercise. Long tones
 * take their clock from the audio they are already analysing, which cannot drift at all; an exercise
 * that records nothing has no such stream to read, and this is the next best thing.
 *
 * [nanoTime] is injectable so tests can run on virtual time.
 */
class MonotonicBeatClock(
    private val tickIntervalMs: Long = DEFAULT_TICK_MS,
    private val nanoTime: () -> Long = System::nanoTime,
) : BeatClock {

    override fun elapsedBeats(tempo: Tempo): Flow<Beats> = flow {
        val start = nanoTime()
        emit(Beats.ZERO)
        while (true) {
            delay(tickIntervalMs)
            val seconds = (nanoTime() - start) / NANOS_PER_SECOND
            emit(tempo.beatsFor(seconds))
        }
    }

    private companion object {
        const val DEFAULT_TICK_MS = 25L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

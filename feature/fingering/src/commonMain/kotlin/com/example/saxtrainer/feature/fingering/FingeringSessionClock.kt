package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.monotonicNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Elapsed time for an exercise that has nothing to listen to.
 *
 * Long Tones takes its clock from the audio it is already analysing, which cannot drift. This
 * trainer records nothing, so it needs a clock of its own - but the principle is unchanged: the
 * emitted value is read from a monotonic source each tick rather than accumulated from the sleeps,
 * so a late tick shortens the next interval instead of stretching the exercise. Compose only renders
 * what this produces; it never drives the timing.
 *
 * [nanoTime] is injectable so tests can run on virtual time.
 */
class FingeringSessionClock(
    private val tickIntervalMs: Long,
    private val nanoTime: () -> Long = monotonicNanos,
) {
    /** Seconds since collection started, emitted about every [tickIntervalMs]. */
    fun elapsedSeconds(): Flow<Float> = flow {
        val start = nanoTime()
        emit(0f)
        while (true) {
            delay(tickIntervalMs)
            emit((nanoTime() - start) / NANOS_PER_SECOND)
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

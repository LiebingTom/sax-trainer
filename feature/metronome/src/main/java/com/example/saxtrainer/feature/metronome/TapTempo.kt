package com.example.saxtrainer.feature.metronome

import com.example.saxtrainer.feature.metronome.engine.MetronomeEngine

/**
 * Derives a tempo from tap timestamps. Uses a monotonic clock supplied by the caller (nanoseconds)
 * so it is unaffected by wall-clock changes and can be unit tested without Android.
 *
 * Taps further apart than [RESET_AFTER_NANOS] start a new measurement.
 */
class TapTempo(
    private val maxIntervals: Int = DEFAULT_MAX_INTERVALS,
) {
    private val intervals = ArrayDeque<Long>()
    private var lastTapNanos: Long? = null

    /** Records a tap and returns the estimated BPM, or null while there is not enough data. */
    fun tap(nowNanos: Long): Int? {
        val previous = lastTapNanos
        lastTapNanos = nowNanos
        if (previous == null) return null

        val interval = nowNanos - previous
        if (interval > RESET_AFTER_NANOS || interval <= 0) {
            intervals.clear()
            return null
        }

        intervals.addLast(interval)
        while (intervals.size > maxIntervals) intervals.removeFirst()

        val average = intervals.sum().toDouble() / intervals.size
        val bpm = (NANOS_PER_MINUTE / average).toInt()
        return bpm.coerceIn(MetronomeEngine.MIN_BPM, MetronomeEngine.MAX_BPM)
    }

    fun reset() {
        intervals.clear()
        lastTapNanos = null
    }

    private companion object {
        const val DEFAULT_MAX_INTERVALS = 4
        const val RESET_AFTER_NANOS = 2_000_000_000L
        const val NANOS_PER_MINUTE = 60_000_000_000.0
    }
}

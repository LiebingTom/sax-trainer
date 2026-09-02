package com.example.saxtrainer.core.dsp.level

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Level measurements on a block of samples normalised to -1.0..1.0.
 *
 * Stateless and allocation free, so it can run on every captured frame.
 */
object RmsCalculator {

    /** Lowest level reported instead of negative infinity for digital silence. */
    const val MIN_DBFS = -120f

    fun rms(samples: FloatArray, count: Int = samples.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val value = samples[i].toDouble()
            sum += value * value
        }
        return sqrt(sum / count).toFloat()
    }

    fun peak(samples: FloatArray, count: Int = samples.size): Float {
        var peak = 0f
        for (i in 0 until count) {
            val value = abs(samples[i])
            if (value > peak) peak = value
        }
        return peak
    }

    /** Converts a linear amplitude (1.0 = full scale) to dBFS, floored at [MIN_DBFS]. */
    fun toDbfs(amplitude: Float): Float {
        if (amplitude <= 0f) return MIN_DBFS
        return (20.0 * log10(amplitude.toDouble())).toFloat().coerceAtLeast(MIN_DBFS)
    }

    /** Maps a dBFS value to 0..1 for meters, where [floorDbfs] is the bottom of the scale. */
    fun normalizeDbfs(dbfs: Float, floorDbfs: Float = -60f): Float {
        if (floorDbfs >= 0f) return 0f
        return ((dbfs - floorDbfs) / -floorDbfs).coerceIn(0f, 1f)
    }
}

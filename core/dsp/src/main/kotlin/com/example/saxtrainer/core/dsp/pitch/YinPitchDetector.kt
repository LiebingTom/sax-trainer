package com.example.saxtrainer.core.dsp.pitch

import kotlin.math.max

/**
 * Settings of the YIN estimator.
 *
 * [threshold] is the absolute threshold on the normalised difference: lower values are stricter and
 * reject more, higher values accept noisier input. The frequency limits bound the lag search, which
 * is what keeps the algorithm affordable in real time.
 */
data class YinConfig(
    val threshold: Float = 0.15f,
    val minFrequencyHz: Float = 60f,
    val maxFrequencyHz: Float = 1_500f,
    val windowSize: Int = 2048,
) {
    init {
        require(minFrequencyHz > 0f && maxFrequencyHz > minFrequencyHz) {
            "frequency range must be positive and ordered"
        }
        require(windowSize > 0) { "windowSize must be positive" }
        require(threshold > 0f) { "threshold must be positive" }
    }
}

/**
 * Fundamental frequency estimation with the YIN algorithm (de Cheveigne and Kawahara, 2002).
 *
 * This is a time-domain method: it looks for the lag at which the signal best repeats itself, using
 * the cumulative mean normalised difference function and parabolic interpolation for sub-sample
 * resolution. It therefore reports the fundamental even when a harmonic is the loudest component -
 * which is the normal case for a saxophone, and exactly what picking the strongest FFT bin gets
 * wrong.
 *
 * The lag search is limited by [YinConfig.minFrequencyHz]/[YinConfig.maxFrequencyHz], and the two
 * work arrays are allocated once, so repeated calls on the real-time path allocate nothing. One
 * instance per processing pipeline: not thread safe.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val config: YinConfig = YinConfig(),
) : PitchDetector {

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    private val minLag = max(MIN_LAG, (sampleRate / config.maxFrequencyHz).toInt())
    private val maxLag = (sampleRate / config.minFrequencyHz).toInt()

    private val difference = FloatArray(maxLag + 1)
    private val normalised = FloatArray(maxLag + 1)

    /**
     * Samples needed for one estimate: the integration window plus the longest lag. Callers with
     * shorter blocks get null rather than a guess.
     */
    val requiredSamples: Int = config.windowSize + maxLag

    init {
        require(maxLag > minLag) { "frequency range is too narrow for sample rate $sampleRate" }
    }

    /** Analyses the most recent [requiredSamples] samples of [samples]. */
    override fun detect(samples: FloatArray): PitchEstimate? {
        if (samples.size < requiredSamples) return null
        val offset = samples.size - requiredSamples

        computeDifference(samples, offset)
        normaliseCumulatively()

        val lag = firstLagBelowThreshold() ?: return null
        if (isMultipleOfAShorterPeriod(lag)) return null
        val refinedLag = interpolate(lag)
        if (refinedLag <= 0f) return null

        val frequency = sampleRate / refinedLag
        if (frequency < config.minFrequencyHz || frequency > config.maxFrequencyHz) return null

        val confidence = (1f - normalised[lag]).coerceIn(0f, 1f)
        return PitchEstimate(frequencyHz = frequency, confidence = confidence)
    }

    /** Squared difference between the window and itself delayed by each candidate lag. */
    private fun computeDifference(samples: FloatArray, offset: Int) {
        val window = config.windowSize
        difference[0] = 0f
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (j in 0 until window) {
                val delta = samples[offset + j] - samples[offset + j + lag]
                sum += delta.toDouble() * delta
            }
            difference[lag] = sum.toFloat()
        }
    }

    /**
     * Cumulative mean normalisation. Dividing by the running mean removes the bias towards lag zero,
     * which is what stops the estimator from locking onto an upper harmonic.
     */
    private fun normaliseCumulatively() {
        normalised[0] = 1f
        var runningSum = 0.0
        for (lag in 1..maxLag) {
            runningSum += difference[lag]
            normalised[lag] = if (runningSum <= 0.0) {
                1f
            } else {
                (difference[lag] * lag / runningSum).toFloat()
            }
        }
    }

    /**
     * First lag whose normalised difference dips below the threshold, walking down to the bottom of
     * that dip. Null means the block was not periodic enough to call it pitched.
     */
    private fun firstLagBelowThreshold(): Int? {
        var lag = minLag
        while (lag <= maxLag) {
            if (normalised[lag] < config.threshold) {
                while (lag + 1 <= maxLag && normalised[lag + 1] < normalised[lag]) {
                    lag++
                }
                return lag
            }
            lag++
        }
        return null
    }

    /**
     * A signal repeating every N samples also repeats every 2N, 3N and so on. When the real period
     * is shorter than the searched range, the first dip found is such a multiple, which would be
     * reported as a tone an octave or a twelfth too low. The difference function is available for
     * every lag, including those below the search range, so the sub-multiples can simply be checked:
     * if one of them repeats just as well, the true pitch is above the configured maximum and this
     * detector must say nothing rather than report a subharmonic.
     */
    private fun isMultipleOfAShorterPeriod(lag: Int): Boolean {
        for (divisor in 2..MAX_SUBMULTIPLE_DIVISOR) {
            val shorter = lag / divisor
            if (shorter < MIN_LAG) break
            if (normalised[shorter] < config.threshold) return true
        }
        return false
    }

    /** Parabola through the minimum and its neighbours, for resolution finer than one sample. */
    private fun interpolate(lag: Int): Float {
        val previous = if (lag > 0) normalised[lag - 1] else normalised[lag]
        val next = if (lag < maxLag) normalised[lag + 1] else normalised[lag]
        val curvature = previous + next - 2f * normalised[lag]
        if (curvature <= 0f) return lag.toFloat()
        return lag + 0.5f * (previous - next) / curvature
    }

    private companion object {
        const val MIN_LAG = 2
        const val MAX_SUBMULTIPLE_DIVISOR = 4
    }
}

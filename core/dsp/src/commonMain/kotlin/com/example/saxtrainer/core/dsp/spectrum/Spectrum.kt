package com.example.saxtrainer.core.dsp.spectrum

import kotlin.math.roundToInt

/**
 * Positive-frequency magnitude spectrum of one analysis frame.
 *
 * The whole positive half is always computed; which part a screen draws is a UI decision. The
 * magnitude array is reused by the analyzer that owns this instance, so anything that outlives one
 * analysis pass must take a copy.
 */
class Spectrum(
    val fftSize: Int,
    val sampleRate: Int,
) {
    init {
        require(fftSize > 1) { "fftSize must be greater than one" }
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    /** Amplitude per bin, index 0 = DC, last index = Nyquist. */
    val magnitudes: FloatArray = FloatArray(fftSize / 2 + 1)

    val binCount: Int get() = magnitudes.size

    /** Frequency distance between two neighbouring bins. */
    val binWidthHz: Float = sampleRate.toFloat() / fftSize

    fun frequencyAt(bin: Int): Float = bin * binWidthHz

    /** Nearest bin for [frequencyHz], clamped to the available range. */
    fun binFor(frequencyHz: Float): Int =
        (frequencyHz / binWidthHz).roundToInt().coerceIn(0, binCount - 1)

    /** Index of the strongest bin at or above [minBin]. Returns -1 for an all-zero spectrum. */
    fun peakBin(minBin: Int = 1): Int {
        var best = -1
        var bestValue = 0f
        for (bin in minBin until binCount) {
            val value = magnitudes[bin]
            if (value > bestValue) {
                bestValue = value
                best = bin
            }
        }
        return best
    }
}

package com.example.saxtrainer.core.dsp.window

import kotlin.math.PI
import kotlin.math.cos

/**
 * A window applied to a block of samples before a transform.
 *
 * Implementations only describe their coefficients; applying them and knowing about the FFT is not
 * their job. Hamming, Blackman or flat-top windows can therefore be added later without touching
 * the FFT or any analyzer.
 */
interface WindowFunction {
    val name: String

    /** Window coefficients for [size] samples. Implementations may return a cached array. */
    fun coefficients(size: Int): FloatArray

    /**
     * Mean of the coefficients. Used to compensate the amplitude loss the window causes, so
     * magnitudes stay comparable between windows.
     */
    fun coherentGain(size: Int): Float {
        val values = coefficients(size)
        var sum = 0.0
        for (value in values) sum += value
        return (sum / size).toFloat()
    }
}

/** Caches the coefficients for the last requested size; analysis usually keeps one size forever. */
abstract class CachedWindowFunction : WindowFunction {
    private var cachedSize = -1
    private var cached: FloatArray = FloatArray(0)

    final override fun coefficients(size: Int): FloatArray {
        require(size > 0) { "window size must be positive" }
        if (size != cachedSize) {
            cached = compute(size)
            cachedSize = size
        }
        return cached
    }

    protected abstract fun compute(size: Int): FloatArray
}

/** Periodic Hann window, the default for spectrum analysis in this app. */
class HannWindow : CachedWindowFunction() {
    override val name: String = "Hann"

    override fun compute(size: Int): FloatArray = FloatArray(size) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
    }
}

/** No windowing. Useful as a baseline and for block processing that must not be tapered. */
class RectangularWindow : CachedWindowFunction() {
    override val name: String = "Rectangular"

    override fun compute(size: Int): FloatArray = FloatArray(size) { 1f }
}

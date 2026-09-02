package com.example.saxtrainer.core.dsp.fft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 FFT for a fixed [size].
 *
 * Twiddle factors and the bit-reversal permutation are precomputed once, so a transform performs no
 * allocation and no trigonometry. The class knows nothing about sample rates or audio - callers
 * attach that meaning.
 */
class Fft(val size: Int) {

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
    }

    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val reversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) {
            table[i] = Integer.reverse(i) ushr (Int.SIZE_BITS - bits)
        }
    }

    /**
     * Transforms [real]/[imaginary] in place. Both arrays must have [size] elements; for real input
     * pass an imaginary array of zeros.
     */
    fun transform(real: FloatArray, imaginary: FloatArray) {
        require(real.size == size && imaginary.size == size) {
            "input arrays must have $size elements"
        }

        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var swap = real[i]; real[i] = real[j]; real[j] = swap
                swap = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = swap
            }
        }

        var span = 2
        while (span <= size) {
            val half = span / 2
            val step = size / span
            var start = 0
            while (start < size) {
                var twiddle = 0
                for (offset in start until start + half) {
                    val partner = offset + half
                    val cosValue = cosTable[twiddle]
                    val sinValue = sinTable[twiddle]
                    val realPart = real[partner] * cosValue + imaginary[partner] * sinValue
                    val imagPart = imaginary[partner] * cosValue - real[partner] * sinValue
                    real[partner] = real[offset] - realPart
                    imaginary[partner] = imaginary[offset] - imagPart
                    real[offset] += realPart
                    imaginary[offset] += imagPart
                    twiddle += step
                }
                start += span
            }
            span = span shl 1
        }
    }
}

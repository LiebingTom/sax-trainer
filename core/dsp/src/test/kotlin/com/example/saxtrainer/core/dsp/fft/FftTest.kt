package com.example.saxtrainer.core.dsp.fft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The transform had no test of its own; the spectrum analyser covered it indirectly. It has one now
 * because the bit-reversal table is hand-written - `Integer.reverse` is JVM-only - and an ordering
 * mistake there does not crash, it quietly scrambles the spectrum.
 */
class FftTest {

    private fun magnitudes(real: FloatArray): FloatArray {
        val imaginary = FloatArray(real.size)
        Fft(real.size).transform(real, imaginary)
        return FloatArray(real.size) { hypot(real[it], imaginary[it]) }
    }

    @Test
    fun `an impulse spreads evenly across every bin`() {
        // The one input whose answer is known for every bin at once, so a scrambled ordering cannot
        // hide: it would still be flat, but the phase check below would not survive it.
        val real = FloatArray(16).also { it[0] = 1f }
        magnitudes(real).forEach { assertEquals(1.0, it.toDouble(), 1e-5) }
    }

    @Test
    fun `a delayed impulse puts each bin where its own phase says`() {
        // Sample n alone transforms to exp(-2*pi*i*k*n/N): every bin still has magnitude one, but
        // the real parts trace a cosine. Getting the bin order wrong reorders that cosine.
        val size = 16
        val delay = 3
        val real = FloatArray(size).also { it[delay] = 1f }
        val imaginary = FloatArray(size)
        Fft(size).transform(real, imaginary)

        for (k in 0 until size) {
            val angle = -2.0 * PI * k * delay / size
            assertEquals("bin $k real", cos(angle), real[k].toDouble(), 1e-5)
            assertEquals("bin $k imaginary", sin(angle), imaginary[k].toDouble(), 1e-5)
        }
    }

    @Test
    fun `a cosine at one bin lands in that bin and its mirror`() {
        val size = 32
        val bin = 5
        val real = FloatArray(size) { cos(2.0 * PI * bin * it / size).toFloat() }

        val magnitudes = magnitudes(real)

        assertEquals(size / 2.0, magnitudes[bin].toDouble(), 1e-3)
        assertEquals(size / 2.0, magnitudes[size - bin].toDouble(), 1e-3)
        magnitudes.forEachIndexed { index, magnitude ->
            if (index != bin && index != size - bin) {
                assertEquals("bin $index should be empty", 0.0, magnitude.toDouble(), 1e-3)
            }
        }
    }

    @Test
    fun `a constant signal is all in the first bin`() {
        val magnitudes = magnitudes(FloatArray(8) { 1f })

        assertEquals(8.0, magnitudes[0].toDouble(), 1e-5)
        magnitudes.drop(1).forEach { assertEquals(0.0, it.toDouble(), 1e-5) }
    }

    @Test
    fun `every size from two upwards transforms`() {
        // The bit-reversal is width-dependent, so one size passing says little about the next.
        var size = 2
        while (size <= 1024) {
            val bin = 1
            val real = FloatArray(size) { cos(2.0 * PI * bin * it / size).toFloat() }
            val peak = magnitudes(real).withIndex().maxBy { it.value }.index
            assertEquals("size $size", bin, peak)
            size *= 2
        }
    }

    @Test
    fun `a size that is not a power of two is refused`() {
        assertThrows(IllegalArgumentException::class.java) { Fft(12) }
        assertThrows(IllegalArgumentException::class.java) { Fft(1) }
    }

    @Test
    fun `input arrays must match the size`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fft(8).transform(FloatArray(8), FloatArray(4))
        }
    }
}

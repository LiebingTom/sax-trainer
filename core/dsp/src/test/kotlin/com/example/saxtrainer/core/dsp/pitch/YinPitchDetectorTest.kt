package com.example.saxtrainer.core.dsp.pitch

import com.example.saxtrainer.core.dsp.sineWave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class YinPitchDetectorTest {

    private val sampleRate = 48_000
    private val detector = YinPitchDetector(sampleRate)
    private val blockSize = 4096

    private fun assertDetects(expectedHz: Double, samples: FloatArray, tolerancePercent: Double = 0.5) {
        val estimate = detector.detect(samples)
        assertNotNull("no pitch detected for $expectedHz Hz", estimate)
        estimate!!
        val error = abs(estimate.frequencyHz - expectedHz) / expectedHz * 100
        assertTrue(
            "detected ${estimate.frequencyHz} Hz for $expectedHz Hz",
            error < tolerancePercent,
        )
        assertTrue("confidence was ${estimate.confidence}", estimate.confidence > 0.8f)
    }

    @Test
    fun `detects concert a`() {
        assertDetects(440.0, sineWave(440.0, sampleRate, blockSize))
    }

    @Test
    fun `detects a low tone`() {
        assertDetects(196.0, sineWave(196.0, sampleRate, blockSize))
    }

    @Test
    fun `detects a high tone`() {
        assertDetects(1000.0, sineWave(1000.0, sampleRate, blockSize))
    }

    @Test
    fun `detects a non integer frequency`() {
        assertDetects(233.08, sineWave(233.08, sampleRate, blockSize))
    }

    @Test
    fun `finds the fundamental when a harmonic is the loudest component`() {
        val fundamental = 220.0
        // Second harmonic dominates and the fundamental is weak: choosing the strongest spectral
        // component would report 440 Hz.
        val samples = FloatArray(blockSize) { i ->
            val t = i.toDouble() / sampleRate
            val value = 0.1 * sin(2 * PI * fundamental * t) +
                0.8 * sin(2 * PI * 2 * fundamental * t) +
                0.4 * sin(2 * PI * 3 * fundamental * t)
            value.toFloat()
        }

        assertDetects(fundamental, samples)
    }

    @Test
    fun `handles a harmonically rich tone like a wind instrument`() {
        val fundamental = 311.13
        val samples = FloatArray(blockSize) { i ->
            val t = i.toDouble() / sampleRate
            var value = 0.0
            for (harmonic in 1..6) {
                value += (1.0 / harmonic) * sin(2 * PI * harmonic * fundamental * t)
            }
            (value * 0.3).toFloat()
        }

        assertDetects(fundamental, samples)
    }

    @Test
    fun `silence has no pitch`() {
        assertNull(detector.detect(FloatArray(blockSize)))
    }

    @Test
    fun `noise is rejected or reported with low confidence`() {
        val random = Random(seed = 42)
        val noise = FloatArray(blockSize) { (random.nextFloat() * 2f - 1f) * 0.5f }

        val estimate = detector.detect(noise)
        if (estimate != null) {
            assertTrue("noise reported confidence ${estimate.confidence}", estimate.confidence < 0.75f)
        }
    }

    @Test
    fun `frequencies outside the configured range are not reported`() {
        val detector = YinPitchDetector(sampleRate, YinConfig(minFrequencyHz = 200f, maxFrequencyHz = 800f))

        assertNull("30 Hz is below the range", detector.detect(sineWave(30.0, sampleRate, blockSize)))
        assertNull("2 kHz is above the range", detector.detect(sineWave(2000.0, sampleRate, blockSize)))
    }

    @Test
    fun `blocks shorter than the analysis window are rejected`() {
        val short = FloatArray(detector.requiredSamples - 1)
        assertNull(detector.detect(short))
    }

    @Test
    fun `amplitude does not change the detected frequency`() {
        val loud = detector.detect(sineWave(440.0, sampleRate, blockSize, amplitude = 0.9))!!
        val quiet = detector.detect(sineWave(440.0, sampleRate, blockSize, amplitude = 0.02))!!

        assertEquals(loud.frequencyHz, quiet.frequencyHz, 0.5f)
    }

    @Test
    fun `repeated calls on one detector stay consistent`() {
        val samples = sineWave(392.0, sampleRate, blockSize)
        val first = detector.detect(samples)!!

        repeat(20) {
            val estimate = detector.detect(samples)!!
            assertEquals(first.frequencyHz, estimate.frequencyHz, 1e-4f)
        }
    }
}

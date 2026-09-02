package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.dsp.level.RmsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LongToneProcessorTest {

    private val config = LongToneConfig()
    private val sampleRate = config.capture.sampleRate
    private val frameSize = config.capture.frameSize
    private val hopSize = config.capture.hopSize

    private fun processor() = LongToneProcessor(config, sampleRate)

    private fun sine(frequencyHz: Double, amplitude: Double = 1.0) = FloatArray(frameSize) { i ->
        (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate)).toFloat()
    }

    @Test
    fun `level of a full scale sine is propagated to the measurements`() {
        val processor = processor()
        processor.processFrame(sine(1000.0), frameSize)

        val measurements = processor.measurements()
        // A full scale sine has an RMS of 1/sqrt(2), which is -3.01 dBFS.
        assertEquals(-3.01f, measurements.rmsDbfs, 0.05f)
        assertEquals(0f, measurements.peakDbfs, 0.05f)
        assertTrue(measurements.levelFraction > 0.9f)
    }

    @Test
    fun `gate opens for a loud tone and stays closed for silence`() {
        val loud = processor()
        loud.processFrame(sine(440.0, amplitude = 0.5), frameSize)
        assertTrue(loud.measurements().signalPresent)

        val quiet = processor()
        quiet.processFrame(FloatArray(frameSize), frameSize)
        assertFalse(quiet.measurements().signalPresent)
    }

    @Test
    fun `a tone below the gate threshold does not count as sounding`() {
        val processor = processor()
        // -60 dBFS peak is well below the configured open threshold of -42 dBFS.
        processor.processFrame(sine(440.0, amplitude = 0.001), frameSize)

        assertFalse(processor.measurements().signalPresent)
        assertEquals(0f, processor.soundingSeconds, 1e-6f)
    }

    @Test
    fun `spectrum bands peak at the played frequency`() {
        val processor = processor()
        processor.processFrame(sine(1000.0), frameSize)

        val bands = processor.measurements().spectrumBands
        assertEquals(config.displayBands, bands.size)

        val loudestBand = bands.indices.maxBy { bands[it] }
        val hzPerBand = config.displayMaxFrequencyHz / bands.size
        val bandCentreHz = (loudestBand + 0.5f) * hzPerBand
        assertTrue(
            "loudest band was around $bandCentreHz Hz",
            abs(bandCentreHz - 1000f) <= 2 * hzPerBand,
        )
    }

    @Test
    fun `bands are limited to the displayed frequency range`() {
        val processor = processor()
        // Above the 3 kHz display limit, so no band should stand out.
        processor.processFrame(sine(6000.0), frameSize)

        val bands = processor.measurements().spectrumBands
        assertTrue("bands were ${bands.max()}", bands.max() < 0.05f)
    }

    @Test
    fun `elapsed audio time comes from sample counts, not from call count`() {
        val processor = processor()
        processor.processFrame(sine(440.0), frameSize)
        assertEquals(frameSize.toFloat() / sampleRate, processor.analyzedSeconds, 1e-6f)

        repeat(4) { processor.processFrame(sine(440.0), hopSize) }
        val expectedSamples = frameSize + 4 * hopSize
        assertEquals(expectedSamples.toFloat() / sampleRate, processor.analyzedSeconds, 1e-6f)
    }

    @Test
    fun `sounding time only accumulates while the gate is open`() {
        val processor = processor()
        processor.processFrame(sine(440.0, amplitude = 0.5), hopSize)
        processor.processFrame(FloatArray(frameSize), hopSize)
        processor.processFrame(FloatArray(frameSize), hopSize)

        assertEquals(hopSize.toFloat() / sampleRate, processor.soundingSeconds, 1e-6f)
        assertEquals(3f * hopSize / sampleRate, processor.analyzedSeconds, 1e-6f)

        val result = processor.result(targetSeconds = 10, completed = false)
        assertEquals(1f / 3f, result.steadyFraction, 1e-4f)
        assertEquals(processor.soundingSeconds, result.soundingSeconds, 1e-6f)
        assertEquals(processor.analyzedSeconds, result.heldSeconds, 1e-6f)
    }

    @Test
    fun `average level averages power, not decibels`() {
        val processor = processor()
        processor.processFrame(sine(1000.0), hopSize) // RMS 0.7071
        processor.processFrame(FloatArray(frameSize), hopSize) // silence

        // Mean square of 0.5 and 0 is 0.25, so the average RMS is 0.5 => -6.02 dBFS.
        // Averaging dBFS values instead would give about -61 dB.
        val result = processor.result(targetSeconds = 10, completed = false)
        assertEquals(-6.02f, result.averageLevelDbfs, 0.05f)
    }

    @Test
    fun `average level is weighted by audio duration`() {
        val processor = processor()
        processor.processFrame(sine(1000.0), 3 * hopSize) // three times as much audio
        processor.processFrame(FloatArray(frameSize), hopSize)

        // Mean square = (0.5 * 3 + 0) / 4 = 0.375 => RMS 0.6124 => -4.26 dBFS.
        val result = processor.result(targetSeconds = 10, completed = false)
        assertEquals(-4.26f, result.averageLevelDbfs, 0.05f)
    }

    @Test
    fun `result without any frame is well defined`() {
        val result = processor().result(targetSeconds = 15, completed = false)

        assertEquals(0f, result.heldSeconds, 0f)
        assertEquals(0f, result.soundingSeconds, 0f)
        assertEquals(0f, result.steadyFraction, 0f)
        assertEquals(RmsCalculator.MIN_DBFS, result.averageLevelDbfs, 0f)
        assertEquals(15, result.targetSeconds)
        assertFalse(result.completed)
    }

    @Test
    fun `measurements report the configured display range`() {
        val processor = processor()
        processor.processFrame(sine(440.0), frameSize)

        assertEquals(
            config.displayMaxFrequencyHz,
            processor.measurements().displayMaxFrequencyHz,
            0f,
        )
    }
}

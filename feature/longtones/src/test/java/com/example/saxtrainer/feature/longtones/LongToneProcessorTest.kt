package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Cents
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class LongToneProcessorTest {

    private val config = LongToneConfig()
    private val sampleRate = config.capture.sampleRate
    private val frameSize = config.capture.frameSize
    private val hopSize = config.capture.hopSize

    private val targetNote = config.defaultRoot
    private val targetHz = targetNote.frequencyHz()

    private fun processor(targetFrequencyHz: Double = targetHz) =
        LongToneProcessor(config, sampleRate).apply { startStep(targetFrequencyHz) }

    /** Sine at a known deviation from the target, for the pitch assertions. */
    private fun toneOffTargetBy(cents: Double, amplitude: Double = 0.5) =
        sine(Cents.shift(targetHz, cents), amplitude)

    /** The note under test, held for four beats. */
    private fun step() = ToneStep(targetNote, Beats.of(4))

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

        val result = processor.stepResult(step(), completed = false)
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
        val result = processor.stepResult(step(), completed = false)
        assertEquals(-6.02f, result.averageLevelDbfs, 0.05f)
    }

    @Test
    fun `average level is weighted by audio duration`() {
        val processor = processor()
        processor.processFrame(sine(1000.0), 3 * hopSize) // three times as much audio
        processor.processFrame(FloatArray(frameSize), hopSize)

        // Mean square = (0.5 * 3 + 0) / 4 = 0.375 => RMS 0.6124 => -4.26 dBFS.
        val result = processor.stepResult(step(), completed = false)
        assertEquals(-4.26f, result.averageLevelDbfs, 0.05f)
    }

    @Test
    fun `result without any frame is well defined`() {
        val result = processor().stepResult(step(), completed = false)

        assertEquals(0f, result.heldSeconds, 0f)
        assertEquals(0f, result.soundingSeconds, 0f)
        assertEquals(0f, result.steadyFraction, 0f)
        assertEquals(RmsCalculator.MIN_DBFS, result.averageLevelDbfs, 0f)
        assertEquals(Beats.of(4), result.targetBeats)
        assertEquals(targetNote, result.note)
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

    // --- pitch -------------------------------------------------------------------------------

    @Test
    fun `a tone at the target reads as in tune`() {
        val processor = processor()
        processor.processFrame(toneOffTargetBy(0.0), frameSize)

        val pitch = processor.measurements().pitch!!
        assertEquals(0f, pitch.cents, 2f)
        assertEquals(targetHz.toFloat(), pitch.frequencyHz, 1f)
        assertEquals(targetNote, pitch.detectedNote)
        assertFalse(pitch.offTarget)
        assertTrue(pitch.confidence > 0.8f)
    }

    @Test
    fun `a sharp tone reads positive and a flat tone negative`() {
        val sharp = processor()
        sharp.processFrame(toneOffTargetBy(10.0), frameSize)
        assertEquals(10f, sharp.measurements().pitch!!.cents, 2f)

        val flat = processor()
        flat.processFrame(toneOffTargetBy(-25.0), frameSize)
        assertEquals(-25f, flat.measurements().pitch!!.cents, 2f)
    }

    @Test
    fun `pitch is reported for a harmonically rich tone, not for its loudest harmonic`() {
        val processor = processor()
        val samples = FloatArray(frameSize) { i ->
            val t = i.toDouble() / sampleRate
            var value = 0.0
            for (harmonic in 1..5) {
                val weight = if (harmonic == 2) 1.0 else 0.3
                value += weight * sin(2 * PI * harmonic * targetHz * t)
            }
            (value * 0.2).toFloat()
        }
        processor.processFrame(samples, frameSize)

        val pitch = processor.measurements().pitch!!
        assertEquals(0f, pitch.cents, 5f)
        assertEquals(targetNote, pitch.detectedNote)
    }

    @Test
    fun `a note far from the target is flagged as a different note`() {
        val processor = processor()
        // An octave above the target: still a valid estimate, but not the requested note.
        processor.processFrame(sine(targetHz * 2, amplitude = 0.5), frameSize)

        val pitch = processor.measurements().pitch!!
        assertTrue(pitch.offTarget)
        assertEquals(1200f, pitch.cents, 5f)
        assertEquals(targetNote.transposedBy(12), pitch.detectedNote)
    }

    @Test
    fun `no pitch is reported below the signal gate`() {
        val processor = processor()
        // Audible frequency, but far too quiet to pass the gate.
        processor.processFrame(toneOffTargetBy(0.0, amplitude = 0.0005), frameSize)

        assertNull(processor.measurements().pitch)
        assertNull(processor.stepResult(step(), completed = false).averagePitchCents)
        assertNull(processor.stepResult(step(), completed = false).pitchStabilityCents)
    }

    @Test
    fun `no pitch is reported for loud noise`() {
        val processor = processor()
        val random = Random(seed = 7)
        val noise = FloatArray(frameSize) { (random.nextFloat() * 2f - 1f) * 0.5f }
        processor.processFrame(noise, frameSize)

        assertNull(processor.measurements().pitch)
        assertNull(processor.stepResult(step(), completed = false).averagePitchCents)
    }

    @Test
    fun `average pitch is the mean of the accepted estimates`() {
        val processor = processor()
        listOf(-10.0, 0.0, 20.0, 30.0).forEach { cents ->
            processor.processFrame(toneOffTargetBy(cents), hopSize)
        }

        assertEquals(10f, processor.stepResult(step(), completed = false).averagePitchCents!!, 2f)
    }

    @Test
    fun `frames without a usable pitch do not shift the average`() {
        val processor = processor()
        processor.processFrame(toneOffTargetBy(20.0), hopSize)
        processor.processFrame(FloatArray(frameSize), hopSize) // silence
        processor.processFrame(toneOffTargetBy(20.0), hopSize)

        assertEquals(20f, processor.stepResult(step(), completed = false).averagePitchCents!!, 2f)
        assertEquals(0f, processor.stepResult(step(), completed = false).pitchStabilityCents!!, 2f)
    }

    @Test
    fun `stability is the deviation around the mean, not the deviation from the target`() {
        val processor = processor()
        // Steady but consistently flat: bad average, good stability.
        repeat(4) { processor.processFrame(toneOffTargetBy(-30.0), hopSize) }

        assertEquals(-30f, processor.stepResult(step(), completed = false).averagePitchCents!!, 2f)
        assertEquals(0f, processor.stepResult(step(), completed = false).pitchStabilityCents!!, 1f)
    }

    @Test
    fun `wavering pitch produces a larger stability figure`() {
        val processor = processor()
        listOf(10.0, -10.0, 10.0, -10.0).forEach { cents ->
            processor.processFrame(toneOffTargetBy(cents), hopSize)
        }

        assertEquals(0f, processor.stepResult(step(), completed = false).averagePitchCents!!, 2f)
        assertEquals(10f, processor.stepResult(step(), completed = false).pitchStabilityCents!!, 2f)
    }

    @Test
    fun `the result carries the pitch statistics`() {
        val processor = processor()
        listOf(4.0, 8.0).forEach { processor.processFrame(toneOffTargetBy(it), hopSize) }

        val result = processor.stepResult(step(), completed = true)
        assertEquals(6f, result.averagePitchCents!!, 2f)
        assertEquals(2f, result.pitchStabilityCents!!, 2f)
        // The level statistics are unaffected by the pitch analysis.
        assertEquals(processor.analyzedSeconds, result.heldSeconds, 1e-6f)
        assertTrue(result.averageLevelDbfs > -20f)
    }

    @Test
    fun `an attempt without a usable pitch reports no pitch statistics`() {
        val processor = processor()
        repeat(3) { processor.processFrame(FloatArray(frameSize), hopSize) }

        val result = processor.stepResult(step(), completed = false)
        assertNull(result.averagePitchCents)
        assertNull(result.pitchStabilityCents)
        assertEquals(0f, result.soundingSeconds, 0f)
    }

    @Test
    fun `pitch is measured against the configured target, not the nearest note`() {
        // Target deliberately between two notes: the deviation must be relative to it.
        val betweenNotes = Cents.shift(Note(NoteName.A, 4).frequencyHz(), 40.0)
        val processor = processor(targetFrequencyHz = betweenNotes)
        processor.processFrame(sine(betweenNotes, amplitude = 0.5), frameSize)

        val pitch = processor.measurements().pitch!!
        assertEquals(0f, pitch.cents, 2f)
        assertEquals(Note(NoteName.A, 4), pitch.detectedNote)
    }
}

package com.example.saxtrainer.core.dsp.spectrum

import com.example.saxtrainer.core.dsp.sineWave
import com.example.saxtrainer.core.dsp.window.HannWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpectrumAnalyzerTest {

    private val sampleRate = 48_000
    private val fftSize = 4096

    private fun analyzer() = SpectrumAnalyzer(
        config = SpectrumConfig(fftSize = fftSize, window = HannWindow()),
        sampleRate = sampleRate,
    )

    @Test
    fun `1 kHz sine produces a peak at 1 kHz`() {
        val analyzer = analyzer()
        val spectrum = analyzer.analyze(sineWave(1000.0, sampleRate, fftSize))

        val peakBin = spectrum.peakBin()
        val peakFrequency = spectrum.frequencyAt(peakBin)

        // 4096 bins at 48 kHz are 11.72 Hz apart; the peak may sit one bin either side.
        assertTrue(
            "peak was at $peakFrequency Hz",
            abs(peakFrequency - 1000f) <= 2 * spectrum.binWidthHz,
        )
    }

    @Test
    fun `bin resolution matches sample rate and fft size`() {
        val spectrum = analyzer().spectrum
        assertEquals(sampleRate.toFloat() / fftSize, spectrum.binWidthHz, 1e-4f)
        assertEquals(fftSize / 2 + 1, spectrum.binCount)
        assertEquals(sampleRate / 2f, spectrum.frequencyAt(spectrum.binCount - 1), 1e-2f)
    }

    @Test
    fun `magnitude of a full scale sine is close to one`() {
        val analyzer = analyzer()
        val spectrum = analyzer.analyze(sineWave(1000.0, sampleRate, fftSize, amplitude = 1.0))

        val peak = spectrum.magnitudes[spectrum.peakBin()]
        // Scalloping loss between bins keeps this slightly below the ideal 1.0.
        assertTrue("peak magnitude was $peak", peak in 0.85f..1.05f)
    }

    @Test
    fun `two tones are resolved as separate peaks`() {
        val analyzer = analyzer()
        val low = sineWave(440.0, sampleRate, fftSize, amplitude = 0.5)
        val high = sineWave(1500.0, sampleRate, fftSize, amplitude = 0.5)
        val mixed = FloatArray(fftSize) { low[it] + high[it] }

        val spectrum = analyzer.analyze(mixed)
        val lowBin = spectrum.binFor(440f)
        val highBin = spectrum.binFor(1500f)

        assertTrue(spectrum.magnitudes[lowBin] > 0.3f)
        assertTrue(spectrum.magnitudes[highBin] > 0.3f)
        // Halfway between the tones there should be far less energy.
        val betweenBin = spectrum.binFor(1000f)
        assertTrue(spectrum.magnitudes[betweenBin] < 0.05f)
    }

    @Test
    fun `different sample rate maps the same bin to a different frequency`() {
        val slow = SpectrumAnalyzer(SpectrumConfig(fftSize = fftSize), sampleRate = 24_000)
        val spectrum = slow.analyze(sineWave(1000.0, 24_000, fftSize))
        val peakFrequency = spectrum.frequencyAt(spectrum.peakBin())

        assertTrue(
            "peak was at $peakFrequency Hz",
            abs(peakFrequency - 1000f) <= 2 * spectrum.binWidthHz,
        )
    }

    @Test
    fun `silence produces an empty spectrum`() {
        val spectrum = analyzer().analyze(FloatArray(fftSize))
        assertEquals(-1, spectrum.peakBin())
    }
}

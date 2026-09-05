package com.example.saxtrainer.core.dsp.spectrum

import com.example.saxtrainer.core.dsp.fft.Fft
import com.example.saxtrainer.core.dsp.window.HannWindow
import com.example.saxtrainer.core.dsp.window.WindowFunction
import kotlin.math.sqrt

/**
 * Analysis settings. The sample rate is deliberately absent: it belongs to the audio that is being
 * analysed and is passed in per call, so the same analyzer works with any capture configuration.
 */
data class SpectrumConfig(
    val fftSize: Int = DEFAULT_FFT_SIZE,
    val window: WindowFunction = HannWindow(),
) {
    companion object {
        const val DEFAULT_FFT_SIZE = 4096
    }
}

/**
 * Turns a block of samples into a magnitude [Spectrum]: window, FFT, magnitude.
 *
 * One analyzer owns its scratch buffers and its result spectrum and reuses them for every call, so
 * repeated real-time analysis allocates nothing. Not thread-safe by design - use one analyzer per
 * processing pipeline.
 */
class SpectrumAnalyzer(
    val config: SpectrumConfig = SpectrumConfig(),
    sampleRate: Int,
) {
    private val fft = Fft(config.fftSize)
    private val real = FloatArray(config.fftSize)
    private val imaginary = FloatArray(config.fftSize)
    private val windowCoefficients = config.window.coefficients(config.fftSize)

    /**
     * Scales magnitudes so a full-scale sinusoid reaches amplitude 1.0 regardless of window and FFT
     * size: two-sided spectrum folded to one side, divided by the window's coherent gain.
     */
    private val magnitudeScale =
        2f / (config.fftSize * config.window.coherentGain(config.fftSize))

    /** Reused result. Valid until the next [analyze] call. */
    val spectrum = Spectrum(config.fftSize, sampleRate)

    /**
     * Analyses the last [SpectrumConfig.fftSize] samples of [samples] and returns [spectrum].
     * Shorter input is zero-padded.
     */
    fun analyze(samples: FloatArray): Spectrum {
        val size = config.fftSize
        val offset = maxOf(0, samples.size - size)
        val available = minOf(size, samples.size)

        for (i in 0 until available) {
            real[i] = samples[offset + i] * windowCoefficients[i]
            imaginary[i] = 0f
        }
        for (i in available until size) {
            real[i] = 0f
            imaginary[i] = 0f
        }

        fft.transform(real, imaginary)

        val magnitudes = spectrum.magnitudes
        for (bin in magnitudes.indices) {
            val re = real[bin]
            val im = imaginary[bin]
            magnitudes[bin] = sqrt(re * re + im * im) * magnitudeScale
        }
        // DC and Nyquist appear once in the two-sided spectrum, so they must not be doubled.
        magnitudes[0] /= 2f
        magnitudes[magnitudes.size - 1] /= 2f
        return spectrum
    }
}

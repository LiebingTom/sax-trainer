package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.dsp.level.SignalGate
import com.example.saxtrainer.core.dsp.spectrum.SpectrumAnalyzer
import kotlin.math.sqrt

/**
 * Long-tone specific analysis: it drives the generic DSP components and aggregates their output
 * into what the exercise needs.
 *
 * Everything reusable (RMS, peak, dBFS, gating, windowing, FFT) stays in `core:dsp`; this class only
 * decides how those results are combined, weighted and summarised for a long tone. It knows nothing
 * about Compose and is used from the capture coroutine only, so it is deliberately not thread safe.
 *
 * Progress is accounted in **audio time**: every call is weighted by the number of genuinely new
 * samples the frame carries, so overlapping analysis windows never count the same audio twice and
 * processing latency cannot distort the statistics.
 */
class LongToneProcessor(
    private val config: LongToneConfig,
    private val sampleRate: Int,
) {
    private val analyzer = SpectrumAnalyzer(config.spectrum, sampleRate)
    private val gate = SignalGate(config.gateOpenDbfs, config.gateCloseDbfs)
    private val bands = FloatArray(config.displayBands)
    private val highestDisplayBin = analyzer.spectrum.binFor(config.displayMaxFrequencyHz)

    private var analyzedSamples = 0L
    private var soundingSamples = 0L
    private var meanSquareSum = 0.0
    private var rmsDbfs = RmsCalculator.MIN_DBFS
    private var peakDbfs = RmsCalculator.MIN_DBFS
    private var signalPresent = false

    /** Audio seen so far, in seconds of audio, not of wall time. */
    val analyzedSeconds: Float get() = analyzedSamples.toFloat() / sampleRate

    /**
     * Audio during which the gate was open. This is the quantity a future pitch detector would
     * replace with "seconds of a correctly played target note".
     */
    val soundingSeconds: Float get() = soundingSamples.toFloat() / sampleRate

    /**
     * Analyses one capture window. [newSamples] is how much audio this frame adds to the timeline
     * (the whole window for the first frame, the hop size afterwards).
     *
     * Allocation free: the snapshot for the UI is built separately by [measurements], which the
     * caller only invokes at the UI update rate.
     */
    fun processFrame(samples: FloatArray, newSamples: Int) {
        val rms = RmsCalculator.rms(samples)
        rmsDbfs = RmsCalculator.toDbfs(rms)
        peakDbfs = RmsCalculator.toDbfs(RmsCalculator.peak(samples))
        signalPresent = gate.update(rmsDbfs)

        analyzedSamples += newSamples
        meanSquareSum += rms.toDouble() * rms.toDouble() * newSamples
        if (signalPresent) soundingSamples += newSamples

        analyzer.analyze(samples)
        foldIntoBands(analyzer.spectrum.magnitudes)
    }

    /** Immutable snapshot of the latest frame for the UI. */
    fun measurements(): LongToneMeasurements = LongToneMeasurements(
        rmsDbfs = rmsDbfs,
        peakDbfs = peakDbfs,
        levelFraction = RmsCalculator.normalizeDbfs(rmsDbfs, config.meterFloorDbfs),
        signalPresent = signalPresent,
        spectrumBands = bands.copyOf(),
        displayMaxFrequencyHz = config.displayMaxFrequencyHz,
    )

    /**
     * Summary of the attempt.
     *
     * [LongToneResult.averageLevelDbfs] is a physically meaningful average: mean square power over
     * the captured audio, converted to dBFS at the end. Averaging decibel values directly would
     * weight quiet passages far too strongly.
     */
    fun result(targetSeconds: Int, completed: Boolean): LongToneResult {
        val averageDbfs = if (analyzedSamples == 0L) {
            RmsCalculator.MIN_DBFS
        } else {
            RmsCalculator.toDbfs(sqrt(meanSquareSum / analyzedSamples).toFloat())
        }
        return LongToneResult(
            heldSeconds = analyzedSeconds,
            targetSeconds = targetSeconds,
            soundingSeconds = soundingSeconds,
            averageLevelDbfs = averageDbfs,
            steadyFraction = if (analyzedSamples == 0L) {
                0f
            } else {
                soundingSamples.toFloat() / analyzedSamples
            },
            completed = completed,
        )
    }

    /**
     * Folds the displayed part of the spectrum into [LongToneConfig.displayBands] buckets, keeping
     * the strongest bin per bucket so a narrow partial stays visible.
     */
    private fun foldIntoBands(magnitudes: FloatArray) {
        val usableBins = highestDisplayBin.coerceAtLeast(1)
        val binsPerBand = usableBins.toFloat() / bands.size
        for (band in bands.indices) {
            val start = (band * binsPerBand).toInt()
            val end = ((band + 1) * binsPerBand).toInt().coerceAtLeast(start + 1)
            var peak = 0f
            for (bin in start until minOf(end, magnitudes.size)) {
                val value = magnitudes[bin]
                if (value > peak) peak = value
            }
            bands[band] = peak
        }
    }
}

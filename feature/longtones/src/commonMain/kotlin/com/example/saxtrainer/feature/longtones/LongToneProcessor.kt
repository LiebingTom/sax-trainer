package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.dsp.level.RmsCalculator
import com.example.saxtrainer.core.dsp.level.SignalGate
import com.example.saxtrainer.core.dsp.pitch.YinPitchDetector
import com.example.saxtrainer.core.dsp.spectrum.SpectrumAnalyzer
import com.example.saxtrainer.core.music.Cents
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.ToneStep
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Long-tone specific analysis: it drives the generic DSP components and aggregates their output
 * into what the exercise needs.
 *
 * Everything reusable (RMS, peak, dBFS, gating, windowing, FFT, pitch estimation) stays in
 * `core:dsp`, and cent arithmetic in `core:music`; this class only decides how those results are
 * combined, weighted and summarised. It knows nothing about Compose and is used from the capture
 * coroutine only, so it is deliberately not thread safe.
 *
 * One processor serves a whole exercise: the analysers and their buffers are built once and reused,
 * while [startStep] resets the statistics for each note. Two sets of accumulators are kept, one for
 * the current note and one for the exercise, so both summaries are exact rather than averages of
 * averages.
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
    private val pitchDetector = YinPitchDetector(sampleRate, config.yin)
    private val bands = FloatArray(config.displayBands)
    private val highestDisplayBin = analyzer.spectrum.binFor(config.displayMaxFrequencyHz)

    private var targetFrequencyHz = 0.0

    private val step = Tally()
    private val exercise = Tally()

    private var rmsDbfs = RmsCalculator.MIN_DBFS
    private var peakDbfs = RmsCalculator.MIN_DBFS
    private var signalPresent = false
    private var pitch: PitchMeasurement? = null

    /** Audio seen for the current note, in seconds of audio, not of wall time. */
    val analyzedSeconds: Float get() = step.analyzedSamples.toFloat() / sampleRate

    /** Audio seen since the exercise started. */
    val exerciseSeconds: Float get() = exercise.analyzedSamples.toFloat() / sampleRate

    /**
     * Audio during which the gate was open for the current note. This is the quantity a future
     * exercise rule would narrow to "seconds of a correctly played note" using
     * [PitchMeasurement.offTarget].
     */
    val soundingSeconds: Float get() = step.soundingSamples.toFloat() / sampleRate

    /**
     * Begins a note. Resets the per-note statistics and the gate while keeping the analysers and
     * their buffers alive, so no allocation happens between notes of an exercise.
     */
    fun startStep(targetFrequencyHz: Double) {
        require(targetFrequencyHz > 0.0) { "target frequency must be positive" }
        this.targetFrequencyHz = targetFrequencyHz
        step.reset()
        gate.reset()
        rmsDbfs = RmsCalculator.MIN_DBFS
        peakDbfs = RmsCalculator.MIN_DBFS
        signalPresent = false
        pitch = null
        bands.fill(0f)
    }

    /**
     * Analyses one capture window. [newSamples] is how much audio this frame adds to the timeline
     * (the whole window for the first frame, the hop size afterwards).
     *
     * Allocation free apart from the pitch value object; the snapshot for the UI is built separately
     * by [measurements], which the caller only invokes at the UI update rate.
     */
    fun processFrame(samples: FloatArray, newSamples: Int) {
        val rms = RmsCalculator.rms(samples)
        rmsDbfs = RmsCalculator.toDbfs(rms)
        peakDbfs = RmsCalculator.toDbfs(RmsCalculator.peak(samples))
        signalPresent = gate.update(rmsDbfs)

        step.addAudio(rms, newSamples, signalPresent)
        exercise.addAudio(rms, newSamples, signalPresent)

        analyzer.analyze(samples)
        foldIntoBands(analyzer.spectrum.magnitudes)
        pitch = estimatePitch(samples)
    }

    /**
     * Pitch is only meaningful while a note is sounding: below the gate, or when the estimator is
     * not confident enough, the measurement is dropped rather than shown or averaged.
     */
    private fun estimatePitch(samples: FloatArray): PitchMeasurement? {
        if (!signalPresent || targetFrequencyHz <= 0.0) return null
        val estimate = pitchDetector.detect(samples) ?: return null
        if (estimate.confidence < config.minPitchConfidence) return null

        val cents = Cents.between(estimate.frequencyHz.toDouble(), targetFrequencyHz).toFloat()
        step.addPitch(cents)
        exercise.addPitch(cents)

        return PitchMeasurement(
            frequencyHz = estimate.frequencyHz,
            cents = cents,
            confidence = estimate.confidence,
            detectedNote = Note.nearestTo(estimate.frequencyHz.toDouble()),
            offTarget = abs(cents) > config.offTargetCents,
        )
    }

    /** Immutable snapshot of the latest frame for the UI. */
    fun measurements(): LongToneMeasurements = LongToneMeasurements(
        rmsDbfs = rmsDbfs,
        peakDbfs = peakDbfs,
        levelFraction = RmsCalculator.normalizeDbfs(rmsDbfs, config.meterFloorDbfs),
        signalPresent = signalPresent,
        spectrumBands = bands.copyOf(),
        displayMaxFrequencyHz = config.displayMaxFrequencyHz,
        pitch = pitch,
    )

    /** Summary of the note that has just finished. */
    fun stepResult(toneStep: ToneStep, completed: Boolean): LongToneStepResult = LongToneStepResult(
        note = toneStep.note,
        targetBeats = toneStep.duration,
        heldSeconds = analyzedSeconds,
        soundingSeconds = soundingSeconds,
        averageLevelDbfs = step.averageLevelDbfs(),
        steadyFraction = step.steadyFraction(),
        averagePitchCents = step.averageCents(),
        pitchStabilityCents = step.pitchStabilityCents(),
        completed = completed,
    )

    /**
     * Summary of the whole exercise, accumulated over every note rather than averaged from the note
     * summaries, so a short note cannot weigh as much as a long one.
     */
    fun exerciseResult(
        steps: List<LongToneStepResult>,
        targetSeconds: Float,
        completed: Boolean,
    ): LongToneResult = LongToneResult(
        steps = steps,
        heldSeconds = exerciseSeconds,
        targetSeconds = targetSeconds,
        soundingSeconds = exercise.soundingSamples.toFloat() / sampleRate,
        averageLevelDbfs = exercise.averageLevelDbfs(),
        steadyFraction = exercise.steadyFraction(),
        completed = completed,
        averagePitchCents = exercise.averageCents(),
        pitchStabilityCents = exercise.pitchStabilityCents(),
    )

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

    /**
     * Running statistics over a span of audio.
     *
     * Level is accumulated as mean square power weighted by audio duration, because averaging
     * decibel values directly would weight quiet passages far too strongly. Pitch is counted per
     * accepted estimate instead: every estimate covers the same analysis window, and frames without
     * a usable pitch must not shift the mean.
     */
    private class Tally {
        var analyzedSamples = 0L
            private set
        var soundingSamples = 0L
            private set

        private var meanSquareSum = 0.0
        private var pitchEstimates = 0
        private var centsSum = 0.0
        private var centsSquaredSum = 0.0

        fun reset() {
            analyzedSamples = 0L
            soundingSamples = 0L
            meanSquareSum = 0.0
            pitchEstimates = 0
            centsSum = 0.0
            centsSquaredSum = 0.0
        }

        fun addAudio(rms: Float, newSamples: Int, signalPresent: Boolean) {
            analyzedSamples += newSamples
            meanSquareSum += rms.toDouble() * rms.toDouble() * newSamples
            if (signalPresent) soundingSamples += newSamples
        }

        fun addPitch(cents: Float) {
            pitchEstimates++
            centsSum += cents
            centsSquaredSum += cents.toDouble() * cents
        }

        fun averageLevelDbfs(): Float = if (analyzedSamples == 0L) {
            RmsCalculator.MIN_DBFS
        } else {
            RmsCalculator.toDbfs(sqrt(meanSquareSum / analyzedSamples).toFloat())
        }

        fun steadyFraction(): Float = if (analyzedSamples == 0L) {
            0f
        } else {
            soundingSamples.toFloat() / analyzedSamples
        }

        fun averageCents(): Float? =
            if (pitchEstimates == 0) null else (centsSum / pitchEstimates).toFloat()

        fun pitchStabilityCents(): Float? {
            if (pitchEstimates == 0) return null
            val mean = centsSum / pitchEstimates
            val variance = (centsSquaredSum / pitchEstimates) - mean * mean
            return sqrt(variance.coerceAtLeast(0.0)).toFloat()
        }
    }
}

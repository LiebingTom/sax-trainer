package com.example.saxtrainer.core.dsp.pitch

/**
 * A fundamental frequency estimate.
 *
 * [confidence] is 0..1 and says how periodic the analysed block was; callers decide what they are
 * willing to accept, because a tuner, a long tone exercise and a rhythm tool have different needs.
 */
data class PitchEstimate(
    val frequencyHz: Float,
    val confidence: Float,
)

/**
 * Estimates the fundamental frequency of a block of samples.
 *
 * The interface says nothing about notes, tuning references or instruments: it reports a frequency,
 * and turning that into musical meaning is the caller's job. Implementations are expected to be
 * reusable across features and to allocate nothing per call.
 */
interface PitchDetector {

    /** Returns the estimate, or null when the block has no detectable pitch. */
    fun detect(samples: FloatArray): PitchEstimate?
}

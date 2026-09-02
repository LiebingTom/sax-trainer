package com.example.saxtrainer.core.music

import kotlin.math.ln
import kotlin.math.pow

/**
 * Cent arithmetic: a cent is a hundredth of an equal-tempered semitone.
 *
 * Kept in `core:music` rather than in the DSP layer, because it is music theory rather than signal
 * processing - a detector reports a frequency, this turns it into a musical deviation.
 */
object Cents {

    const val PER_OCTAVE: Int = 1200
    const val PER_SEMITONE: Int = 100

    /** Signed distance from [referenceHz] to [frequencyHz]; positive means sharp. */
    fun between(frequencyHz: Double, referenceHz: Double): Double {
        require(frequencyHz > 0.0 && referenceHz > 0.0) { "frequencies must be positive" }
        return PER_OCTAVE * (ln(frequencyHz / referenceHz) / LN_2)
    }

    /** Frequency [cents] away from [frequencyHz]. */
    fun shift(frequencyHz: Double, cents: Double): Double {
        require(frequencyHz > 0.0) { "frequency must be positive" }
        return frequencyHz * 2.0.pow(cents / PER_OCTAVE)
    }

    private val LN_2 = ln(2.0)
}

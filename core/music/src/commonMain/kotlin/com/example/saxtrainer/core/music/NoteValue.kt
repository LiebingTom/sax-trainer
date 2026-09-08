package com.example.saxtrainer.core.music

import kotlin.math.pow

/**
 * How long a note is *written*, as opposed to how long it lasts.
 *
 * [Beats] says how much time a note takes; this says which symbol a reader sees. The two are two
 * views of the same fact, so the written form is derived from the duration rather than stored beside
 * it - nothing can then disagree with itself.
 *
 * The ratios are given against a quarter-note beat and converted for other beat units by
 * [NotatedDuration.of], which is where the beat unit enters.
 */
enum class NoteValue(val quarters: Double) {
    WHOLE(4.0),
    HALF(2.0),
    QUARTER(1.0),
    EIGHTH(0.5),
    SIXTEENTH(0.25),
    ;

    /** Whole and half notes are drawn hollow; everything shorter is filled in. */
    val isFilledHead: Boolean get() = this != WHOLE && this != HALF

    /** A whole note has no stem at all; the rest do. */
    val hasStem: Boolean get() = this != WHOLE

    /** Flags on a single note, or beams when several are grouped. */
    val beamCount: Int
        get() = when (this) {
            WHOLE, HALF, QUARTER -> 0
            EIGHTH -> 1
            SIXTEENTH -> 2
        }
}

/**
 * A written duration: a note value, possibly dotted.
 *
 * Each dot adds half of what has been counted so far, which is what a dot means. Only the forms this
 * app can draw are modelled; ties, tuplets and rests are not here yet, and adding them belongs
 * beside this type rather than inside it.
 */
data class NotatedDuration(val value: NoteValue, val dots: Int = 0) {

    init {
        require(dots in 0..MAX_DOTS) { "$dots dots is more than this notation draws" }
    }

    /** How long it lasts, in a bar whose beat is [beatUnit]. */
    fun beats(beatUnit: Int = QUARTER_BEAT_UNIT): Beats =
        Beats(value.quarters * dotFactor(dots) * beatUnit / QUARTER_BEAT_UNIT)

    override fun toString(): String = value.name.lowercase() + ".".repeat(dots)

    companion object {
        const val MAX_DOTS = 2

        /** A quarter-note beat, the only unit this app writes in so far. */
        private const val QUARTER_BEAT_UNIT = 4

        private const val TOLERANCE = 1e-9

        /**
         * How [beats] is written in a bar whose beat is [beatUnit], or null when no single note head
         * says it.
         *
         * Null is a real answer, not a failure: a five-beat long tone is a whole note tied to a
         * quarter, and no single head says it. Ask [ToneSequence.notatedReading] for a duration
         * written out in full, ties and bar lines included; this only ever answers about one head.
         */
        fun of(beats: Beats, beatUnit: Int = QUARTER_BEAT_UNIT): NotatedDuration? {
            if (beats.value <= 0.0) return null
            val quarters = beats.value * QUARTER_BEAT_UNIT / beatUnit

            for (value in NoteValue.entries) {
                for (dots in 0..MAX_DOTS) {
                    if (kotlin.math.abs(quarters - value.quarters * dotFactor(dots)) < TOLERANCE) {
                        return NotatedDuration(value, dots)
                    }
                }
            }
            return null
        }

        /** One dot adds a half, two dots add a half and a quarter, and so on. */
        private fun dotFactor(dots: Int): Double = 2.0 - 0.5.pow(dots)
    }
}

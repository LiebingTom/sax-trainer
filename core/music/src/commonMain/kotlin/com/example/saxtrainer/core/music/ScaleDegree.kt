package com.example.saxtrainer.core.music

/**
 * A note counted from the root of its key, the way an improviser names it: "the flat three", "the
 * five an octave up".
 *
 * Counted against the major scale, whatever scale the note came from - the jazz convention - so the
 * same number is always the same distance from the root: the blues reads 1 ♭3 4 ♭5 5 ♭7, natural
 * minor 1 2 ♭3 4 5 ♭6 ♭7, and major plainly 1 to 7.
 */
data class ScaleDegree(
    /** 1 to 7: which step of the major scale the note is counted on. */
    val number: Int,
    /** Semitones away from that step of the major scale: -1 a flat, 0 none, +1 a sharp. */
    val alteration: Int = 0,
    /** Octaves away from the root's own: 0 is home, 1 the octave above, -1 the one below. */
    val octave: Int = 0,
) {
    init {
        require(number in 1..7) { "a degree is counted 1 to 7, not $number" }
        require(alteration in -1..1) { "a degree is at most one flat or sharp, not $alteration" }
    }

    /** The degree as it is said and written, without the octave: "♭3", "5", "♯4". */
    val label: String
        get() = when (alteration) {
            -1 -> "♭$number"
            1 -> "♯$number"
            else -> "$number"
        }

    companion object {
        /** Semitones from the root to each step of the major scale, the yardstick degrees are counted on. */
        internal val MAJOR_INTERVALS = listOf(0, 2, 4, 5, 7, 9, 11)

        /**
         * How a note outside the scale - or in a scale that does not say how it is spelled - is
         * counted, by its semitones above the root: the flats a lead sheet uses.
         */
        private val CHROMATIC = listOf(
            ScaleDegree(1),
            ScaleDegree(2, -1),
            ScaleDegree(2),
            ScaleDegree(3, -1),
            ScaleDegree(3),
            ScaleDegree(4),
            ScaleDegree(5, -1),
            ScaleDegree(5),
            ScaleDegree(6, -1),
            ScaleDegree(6),
            ScaleDegree(7, -1),
            ScaleDegree(7),
        )

        /** The note [semitones] above the root counted without a scale's spelling to go on. */
        fun chromatic(semitones: Int): ScaleDegree =
            CHROMATIC[semitones.mod(Note.SEMITONES_PER_OCTAVE)]
                .copy(octave = semitones.floorDiv(Note.SEMITONES_PER_OCTAVE))
    }
}

package com.example.saxtrainer.core.music

/**
 * A scale as ascending semitone offsets from its root, one octave's worth.
 *
 * [intervals] always starts on `0` - the root itself - so a degree is a plain index into it, not a
 * value that needs the root subtracted back out. Degrees are asked for as 1-based numbers, the way a
 * player names them ("the third"), and [semitoneOffset] is where that numbering meets the zero-based
 * list.
 */
data class Scale(
    val id: String,
    val name: String,
    val intervals: List<Int>,
    /**
     * Which letter each degree is written on, counted in letters up from the root's: 0 is the
     * root's own letter, 2 the third's. How the scale is spelled, beside how it sounds.
     *
     * A seven-note scale gives each letter one degree, and that is the default. A scale of any other
     * size has to say - the blues writes its flat five on the fifth's letter, as G flat in C, and
     * its minor third as E flat - or leave it null, and its notes are then spelled by whatever
     * plays them in the style that music uses.
     */
    val letterSteps: List<Int>? = if (intervals.size == LETTERS_PER_OCTAVE) intervals.indices.toList() else null,
) {
    init {
        require(id.isNotBlank()) { "a scale needs an id" }
        require(intervals.isNotEmpty()) { "scale $id has no degrees" }
        require(intervals.first() == 0) { "scale $id must start on its root" }
        require(letterSteps == null || letterSteps.size == intervals.size) {
            "scale $id names a letter for some degrees and not others"
        }
        require(letterSteps == null || letterSteps.all { it in 0 until LETTERS_PER_OCTAVE }) {
            "scale $id writes a degree outside its own octave of letters"
        }
    }

    /** How many degrees the scale has before it repeats an octave up. */
    val degreeCount: Int get() = intervals.size

    /**
     * Semitones from the root for the 1-based [degree].
     *
     * A degree past [degreeCount] is not an error: the ninth of a seven-note scale is the second,
     * an octave up, and that is what wraps here rather than being rejected. Below 1 wraps the other
     * way: 0 is the top degree an octave down, -1 the one beneath it - the low 6 and 5 a melody
     * dips to under its root.
     */
    fun semitoneOffset(degree: Int): Int {
        val zeroBased = degree - 1
        val octavesUp = zeroBased.floorDiv(degreeCount)
        return intervals[zeroBased.mod(degreeCount)] + octavesUp * Note.SEMITONES_PER_OCTAVE
    }

    /**
     * Letters from the root's to the one the 1-based [degree] is written on, wrapping an octave the
     * way [semitoneOffset] does; null where the scale does not say how it is spelled.
     */
    fun letterStep(degree: Int): Int? {
        val letters = letterSteps ?: return null
        val zeroBased = degree - 1
        return letters[zeroBased.mod(degreeCount)] + zeroBased.floorDiv(degreeCount) * LETTERS_PER_OCTAVE
    }

    /**
     * The note [semitonesFromRoot] above the root, counted as a [ScaleDegree].
     *
     * A note of the scale is counted on the letter the scale writes it on, so the blues' flat five
     * is a ♭5 and not a ♯4, the same way it is spelled on the staff. A note outside the scale, or in
     * a scale that does not say how it is spelled, falls back to [ScaleDegree.chromatic].
     */
    fun degreeOf(semitonesFromRoot: Int): ScaleDegree {
        val fallback = ScaleDegree.chromatic(semitonesFromRoot)
        val index = intervals.indexOf(semitonesFromRoot.mod(Note.SEMITONES_PER_OCTAVE))
        val letter = letterSteps?.getOrNull(index) ?: return fallback
        val alteration = intervals[index] - ScaleDegree.MAJOR_INTERVALS[letter]
        if (alteration !in -1..1) return fallback
        return ScaleDegree(number = letter + 1, alteration = alteration, octave = fallback.octave)
    }

    private companion object {
        const val LETTERS_PER_OCTAVE = 7
    }
}

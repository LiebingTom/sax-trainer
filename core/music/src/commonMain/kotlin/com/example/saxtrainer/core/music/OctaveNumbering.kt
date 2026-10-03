package com.example.saxtrainer.core.music

/**
 * Which number a note's octave is shown with.
 *
 * A display convention only, like [NoteNaming]: a note's octave in the model is always the
 * scientific one, so what is stored, measured and fingered never changes with it. Only the number
 * written after the letter does.
 */
enum class OctaveNumbering(
    val displayName: String,
    val description: String,
    /** Subtracted from the scientific octave to give the one shown. */
    private val offset: Int,
) {
    /** The usual numbering: middle C is C4, and the saxophone's low C is that C. */
    SCIENTIFIC(
        "Scientific",
        "Low C is C4, the palm-key C is C6",
        offset = 0,
    ),

    /**
     * Counted from the saxophone's low C: the low register is 1, the octave key's register is 2, the
     * palm keys start 3. Low B flat and B, the only notes below it, are 0. The same count as the
     * German c¹, c², c³.
     */
    SAXOPHONE(
        "From the sax's low C",
        "Low C is C1, the palm-key C is C3, low B♭ and B are 0",
        offset = 3,
    ),
    ;

    /** The number shown for a note in the scientific [octave]. */
    fun numberOf(octave: Int): Int = octave - offset
}

package com.example.saxtrainer.core.music

/**
 * Distance between what a player reads and what actually sounds.
 *
 * The key an instrument is "in" does not say how far it transposes: a B flat soprano saxophone
 * sounds a tone below the written note, while a B flat tenor sounds a tone *and an octave* below.
 * The entries are therefore named for the instrument they describe rather than for the key alone,
 * and each carries its true distance in semitones.
 */
enum class Transposition(
    val displayName: String,
    val semitonesAboveConcert: Int,
    /** What a written C sounds like, in words, for descriptions in the UI. */
    val writtenCDescription: String,
) {
    CONCERT("Concert pitch", 0, "as written"),

    /** B flat soprano: written C sounds the B flat just below. */
    SOPRANO_B_FLAT("B flat soprano", 2, "a concert B flat, a tone below"),

    /** E flat alto: written C sounds the E flat a major sixth below. */
    ALTO_E_FLAT("E flat alto", 9, "a concert E flat, a major sixth below"),

    /** B flat tenor: written C sounds the B flat an octave and a tone below. */
    TENOR_B_FLAT("B flat tenor", 14, "a concert B flat, an octave and a tone below"),

    /** E flat baritone: written C sounds the E flat an octave and a major sixth below. */
    BARITONE_E_FLAT(
        "E flat baritone",
        21,
        "a concert E flat, an octave and a major sixth below",
    ),
    ;

    /** The note the player reads for a given sounding pitch. */
    fun writtenFor(concert: Note): Note = concert.transposedBy(semitonesAboveConcert)

    /** The pitch that actually sounds for a note the player reads. */
    fun concertFor(written: Note): Note = written.transposedBy(-semitonesAboveConcert)
}

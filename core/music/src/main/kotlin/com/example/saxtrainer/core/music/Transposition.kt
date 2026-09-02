package com.example.saxtrainer.core.music

/**
 * Distance between what a player reads and what actually sounds.
 *
 * A Bb instrument playing a written C sounds a concert Bb, so the written note is a whole tone
 * above concert pitch; an Eb instrument's written C sounds a concert Eb, a major sixth below.
 */
enum class Transposition(val displayName: String, val semitonesAboveConcert: Int) {
    CONCERT("Concert pitch", 0),
    B_FLAT("B flat", 2),
    E_FLAT("E flat", 9),
    ;

    /** The note the player reads for a given sounding pitch. */
    fun writtenFor(concert: Note): Note = concert.transposedBy(semitonesAboveConcert)

    /** The pitch that actually sounds for a note the player reads. */
    fun concertFor(written: Note): Note = written.transposedBy(-semitonesAboveConcert)
}

package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/**
 * How long a held note can be asked to last, in every practice that holds one.
 *
 * Long Tones holds each note of an exercise for this long; the Note Trainer asks for a right note to
 * be kept sounding for this long. One speed and one longest length, so a length means the same in
 * both. Where the offered lengths start is each practice's own: a long tone is not worth a beat.
 *
 * The speed is not a setting. At sixty a beat is a second, so a length needs no explaining and there
 * is nothing on screen to convert. The sequence trainer plays in time and keeps a tempo of its own.
 */
object HeldNote {

    /** The speed held notes are counted at. */
    val tempo: Tempo = Tempo(60)

    /** The longest length offered, in beats. */
    const val LONGEST_BEATS: Int = 16

    /** Every whole-beat length from [shortest] up to [LONGEST_BEATS]. */
    fun beatOptionsFrom(shortest: Int): List<Int> = (shortest..LONGEST_BEATS).toList()
}

package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/**
 * How long a held note can be asked to last, in every practice that holds one.
 *
 * Long Tones holds each note of an exercise for this long; the Note Trainer asks for a right note to
 * be kept sounding for this long. One list and one speed, so a length means the same in both.
 *
 * The speed is not a setting. At sixty a beat is a second, so a length needs no explaining and there
 * is nothing on screen to convert. The sequence trainer plays in time and keeps a tempo of its own.
 */
object HeldNote {

    /** The speed held notes are counted at. */
    val tempo: Tempo = Tempo(60)

    /** The lengths offered, in beats. */
    val beatOptions: List<Int> = listOf(2, 4, 5, 6, 8, 12, 14, 16)

    /** [beatOptions] as durations. */
    val beatChoices: List<Beats> get() = beatOptions.map(Beats::of)
}

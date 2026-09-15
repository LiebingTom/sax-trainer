package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scale
import com.example.saxtrainer.core.music.Scales

/**
 * One scale offered on the setup screen's row of choices, named the way a player thinks of it -
 * "D Major" - rather than as a scale and a root pitch class apart.
 *
 * Deliberately no octave here: the octave a pattern starts in is its own setting, orthogonal to
 * which key is chosen - see [com.example.saxtrainer.core.phrase.PhraseSource.ScalePattern], which
 * combines this with an octave and a degree pattern to name one specific [com.example.saxtrainer
 * .core.music.Note] to build from.
 *
 * A small curated list rather than every scale crossed with every root: the row is meant to be
 * glanced at and tapped, not searched.
 */
data class ScaleChoice(val id: String, val displayName: String, val scale: Scale, val root: NoteName) {
    companion object {
        val defaults: List<ScaleChoice> = listOf(
            ScaleChoice("c_major", "C Major", Scales.MAJOR, NoteName.C),
            ScaleChoice("d_major", "D Major", Scales.MAJOR, NoteName.D),
            ScaleChoice("g_major", "G Major", Scales.MAJOR, NoteName.G),
            ScaleChoice("f_major", "F Major", Scales.MAJOR, NoteName.F),
            ScaleChoice("a_minor", "A Minor", Scales.NATURAL_MINOR, NoteName.A),
        )

        fun byId(id: String): ScaleChoice? = defaults.firstOrNull { it.id == id }
    }
}

package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.Scale

/**
 * A scale on a root - "A Blues", "B♭ Major" - as a scale pattern is played.
 *
 * Every scale of the [ScaleLibrary] on every one of the twelve roots: nothing here is a curated
 * list, so a new scale file is offered in every key without touching code. The id puts the two
 * together ("a_blues", "a_sharp_major") and is what a setup writes down.
 *
 * Deliberately no octave here: the octave a pattern starts in is its own setting, orthogonal to
 * which key is chosen - see [PhraseSource.ScalePattern].
 */
data class ScaleChoice(val scale: Scale, val root: NoteName) {

    val id: String get() = "${root.name.lowercase()}_${scale.id}"

    /** The root as a key is written: B♭ and E♭ rather than A♯ and D♯, and F♯ rather than G♭. */
    val rootName: String get() = NoteNaming.INTERNATIONAL.nameOf(Note(root, 4), rootStyleOf(root))

    val displayName: String get() = "$rootName ${scale.name}"

    companion object {
        /** Every scale of [scales] on every root, scale by scale, each from C upward. */
        fun allIn(scales: List<Scale>): List<ScaleChoice> =
            scales.flatMap { scale -> NoteName.entries.map { root -> ScaleChoice(scale, root) } }

        /** Every choice the bundled scales give. */
        val all: List<ScaleChoice> by lazy { allIn(ScaleLibrary.all) }

        /** C major where there is one, otherwise the first choice there is. */
        fun defaultIn(choices: List<ScaleChoice>): ScaleChoice? =
            choices.firstOrNull { it.root == NoteName.C && it.scale.id == "major" } ?: choices.firstOrNull()

        val default: ScaleChoice? get() = defaultIn(all)

        fun byId(id: String, choices: List<ScaleChoice> = all): ScaleChoice? = choices.firstOrNull { it.id == id }
    }
}

/**
 * How a scale on [root] is written: flats for the four black keys a key is usually named by a flat
 * (D♭, E♭, A♭, B♭), sharps for F♯ and every white key.
 */
fun rootStyleOf(root: NoteName): AccidentalStyle =
    if (root.isAccidental && root != NoteName.F_SHARP) AccidentalStyle.FLATS else AccidentalStyle.SHARPS

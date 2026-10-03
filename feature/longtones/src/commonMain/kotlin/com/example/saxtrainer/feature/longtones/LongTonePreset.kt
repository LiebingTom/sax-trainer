package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a guided lesson, a shortcut and a saved
 * exercise all come through this same door - which is why nothing here mentions lessons.
 *
 * The notes are written notes, as every other practice takes them: what is read. What they sound
 * as depends on the instrument, which is a setting and not part of the exercise.
 */
data class LongTonePreset(
    /** Held one after another, lowest first, each exactly as written. */
    val notes: List<SpelledNote>,
    val beatsPerNote: Beats,
    /** Whether the note after this one is shown beside it, as its name. */
    val showNextNote: Boolean = true,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
    /**
     * Whether the setup screen opens filled in with this rather than being skipped.
     *
     * A launch instruction like [autoStart] and not part of the configuration: it says how the
     * screen opens, not what is practised, and neither is ever written down.
     */
    val editable: Boolean = false,
) {
    init {
        require(notes.isNotEmpty()) { "a preset needs a note to hold" }
        require(beatsPerNote > Beats.ZERO) { "a note must be held for some time" }
    }
}

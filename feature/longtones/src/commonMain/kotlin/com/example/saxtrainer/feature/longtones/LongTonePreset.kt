package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Tempo

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a guided lesson, a shortcut and a saved
 * exercise all come through this same door - which is why nothing here mentions lessons.
 *
 * The pattern travels as its id and the root as a note, exactly as the stored setup writes them:
 * the pattern is the exercise the player chose rather than the notes it produced, and the root is a
 * sounding pitch, because this is the one trainer that measures concert pitch rather than what is
 * written.
 */
data class LongTonePreset(
    /** Id of a pattern in the catalog. One this build no longer has leaves the default selected. */
    val patternId: String,
    val rootNote: Note,
    val beatsPerNote: Beats,
    val tempo: Tempo,
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
        require(patternId.isNotBlank()) { "a preset needs a pattern" }
        require(beatsPerNote > Beats.ZERO) { "a note must be held for some time" }
    }
}

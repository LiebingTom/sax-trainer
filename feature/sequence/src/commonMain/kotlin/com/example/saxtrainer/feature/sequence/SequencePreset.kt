package com.example.saxtrainer.feature.sequence

import com.example.saxtrainer.core.music.Tempo

/**
 * A fully decided exercise: everything the setup screen would otherwise ask the player.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business - a guided lesson, a shortcut, a "play that again"
 * button all come through this same door - which is why nothing here, and nothing in this module,
 * mentions lessons.
 */
data class SequencePreset(
    /** Id of a sequence in the trainer's library. An unknown id leaves the default selected. */
    val sequenceId: String,
    val tempo: Tempo,
    /** Plays the notes back down to where they started before repeating. */
    val addInverse: Boolean = false,
    /** How many times through. Five is a practice session rather than a single read-through. */
    val repeats: Int = 5,
    val metronome: Boolean = true,
    val checkPitch: Boolean = false,
    val checkTiming: Boolean = false,
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
        require(repeats >= 1) { "a sequence is played at least once, not $repeats times" }
    }
}

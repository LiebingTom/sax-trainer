package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.phrase.PhraseSource

/**
 * A fully decided phrase quiz: everything the setup screen would otherwise ask.
 *
 * Handing the quiz a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the quiz's business - a lesson step, a shortcut and a "same again"
 * button all come through this same door - which is why nothing here mentions lessons.
 */
data class PhraseQuizPreset(
    val source: PhraseSource,
    /** How long a unit is shown before it is hidden. */
    val showSeconds: Double = 2.5,
    /** How many times through the whole run. */
    val repeats: Int = 1,
    /** Whether every repeat's units are drawn from one shuffled pool instead of one pass at a time. */
    val mixed: Boolean = false,
    /** Whether the quiz begins by itself rather than waiting for Start. */
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
        require(showSeconds >= 0.0) { "a show time cannot be negative" }
        require(repeats >= 1) { "a quiz is run at least once, not $repeats times" }
    }
}

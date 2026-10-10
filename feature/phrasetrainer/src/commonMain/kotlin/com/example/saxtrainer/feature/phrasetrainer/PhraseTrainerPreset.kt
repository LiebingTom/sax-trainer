package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.designsystem.component.NotationStyle
import com.example.saxtrainer.core.phrase.PhraseSource

/**
 * A fully decided phrase trainer run: everything the setup screen would otherwise ask.
 *
 * Handing the trainer a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the trainer's business, which is why nothing here mentions lessons.
 */
data class PhraseTrainerPreset(
    val source: PhraseSource,
    /** How long a unit is shown before it is hidden. */
    val showSeconds: Double = 2.5,
    /**
     * Whether a note also has to be held for its written length, not just sounded.
     *
     * Independent of pitch-checking, which always happens: turning this off asks only whether the
     * right notes were played, in order, for as long as it takes to be sure - never for how long
     * they were written to last.
     */
    val checkDuration: Boolean = false,
    /** Notes on the staff, or numbers in the key - numbers only where the phrase knows its degrees. */
    val notation: NotationStyle = NotationStyle.STAFF,
    /** How many times through the whole run. */
    val repeats: Int = 1,
    /** Whether every repeat's units are drawn from one shuffled pool instead of one pass at a time. */
    val mixed: Boolean = false,
    /** Whether the trainer begins by itself rather than waiting for Start. */
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
        require(repeats >= 1) { "a trainer run happens at least once, not $repeats times" }
    }
}

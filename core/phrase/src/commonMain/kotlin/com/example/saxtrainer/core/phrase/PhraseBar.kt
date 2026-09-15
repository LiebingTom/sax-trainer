package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.ToneStep

/**
 * One bar's worth of notes, in order.
 *
 * A rest is already just a step with no pitch ([ToneStep.isRest]), so nothing here says anything
 * special about silence. What this type adds over a plain list of steps is the one fact a bar
 * asserts about itself: its steps add up to exactly one bar. [Phrase] checks that against its own
 * time signature once, at construction, rather than trusting every reader to add it up again.
 */
data class PhraseBar(val steps: List<ToneStep>) {
    init {
        require(steps.isNotEmpty()) { "a bar needs at least one step" }
    }
}

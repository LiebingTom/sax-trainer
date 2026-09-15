package com.example.saxtrainer.core.phrase

/**
 * One or more consecutive bars, referenced by index into [Phrase.bars].
 *
 * A unit carries no notes of its own - it is purely a window onto the phrase, resolved through
 * [Phrase.toneSequenceOf]. [Phrase.trainingBars] says how many bars wide every unit of a phrase is;
 * this is just one such window.
 */
data class PracticeUnit(val bars: IntRange) {
    init {
        require(!bars.isEmpty()) { "a practice unit needs at least one bar" }
    }
}

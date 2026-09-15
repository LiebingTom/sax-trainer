package com.example.saxtrainer.feature.phrasequiz

/**
 * Settings of the quiz that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead.
 */
data class PhraseQuizConfig(
    /** How long a unit is shown before it is hidden and has to be rebuilt from memory. */
    val defaultShowSeconds: Double = 2.5,
    /** How long a settled unit stays up before the next one; zero moves on at once. */
    val revealSeconds: Double = 1.5,
    /**
     * How many tiles the option grid is padded out to, with near-pitch decoys - see
     * [decoysFor]. A unit with more notes than this shows exactly its own notes, no fewer.
     */
    val optionGridSize: Int = 18,
)

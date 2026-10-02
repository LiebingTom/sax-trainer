package com.example.saxtrainer.feature.phrasequiz

/**
 * Settings of the quiz that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead.
 */
data class PhraseQuizConfig(
    /** How long a unit is shown before it is hidden and has to be rebuilt from memory. */
    val defaultShowSeconds: Double = 2.5,
    /**
     * How many tiles the option grid is padded out to, with near-pitch decoys - see
     * [decoysFor]. A unit with more notes than this shows exactly its own notes, no fewer.
     *
     * One tile short of two full rows of six: the undo tile takes the first place in the grid, so
     * eleven note tiles beside it still come out to exactly two rows, not two and a leftover one.
     */
    val optionGridSize: Int = 11,
)

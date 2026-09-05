package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings

/**
 * Settings of the quiz that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead.
 */
data class QuizConfig(
    val chart: FingeringChart = SaxophoneFingerings,
    /** Lengths offered on the setup screen; a session should fit in a few minutes. */
    val questionCounts: List<Int> = listOf(10, 20, 30),
    val defaultQuestionCount: Int = 10,
    val optionCount: Int = QuizQuestionGenerator.DEFAULT_OPTION_COUNT,
    /** How long a right answer stays on screen before the next question. */
    val revealCorrectSeconds: Double = 0.9,
    /** Longer for a wrong one, which has a right answer to be read next to it. */
    val revealWrongSeconds: Double = 1.8,
)

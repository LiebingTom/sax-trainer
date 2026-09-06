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
    /** Waits the setup screen offers after a right answer, in seconds; zero moves on at once. */
    val successDelayOptions: List<Double> = listOf(0.0, 1.0, 2.0, 3.0),
    val defaultSuccessDelaySeconds: Double = 1.0,
    /**
     * How long a wrong answer stays on screen, which is longer: it has a right answer to be read
     * next to it. Not the player's to set - what is worth reading takes as long as it takes.
     */
    val revealWrongSeconds: Double = 1.8,
)

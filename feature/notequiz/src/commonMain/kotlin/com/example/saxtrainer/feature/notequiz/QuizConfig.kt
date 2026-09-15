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
    /** Lengths offered on the setup screen, in fives; a session should fit in a few minutes. */
    val questionCounts: List<Int> = (5..30 step 5).toList(),
    val defaultQuestionCount: Int = 10,
    /**
     * Waits after a right answer, in seconds; zero moves on at once. No longer offered on the setup
     * screen, so every quiz waits [defaultSuccessDelaySeconds] - but still a setting of the quiz, for
     * a lesson or a later screen to change.
     */
    val successDelayOptions: List<Double> = listOf(0.0, 1.0, 2.0, 3.0),
    val defaultSuccessDelaySeconds: Double = 1.0,
    /**
     * How long a wrong answer stays on screen, which is longer: it has a right answer to be read
     * next to it. Not the player's to set - what is worth reading takes as long as it takes.
     */
    val revealWrongSeconds: Double = 1.8,
)

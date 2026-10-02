package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.music.SpelledNote

/**
 * A fully decided quiz: everything the setup screen would otherwise ask.
 *
 * Handing the quiz a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the quiz's business - a lesson step, a shortcut and a "same again"
 * button all come through this same door - which is why nothing here mentions lessons.
 */
data class QuizPreset(
    /**
     * Notes the questions are drawn from, each written as it is to be read - E flat and D sharp are
     * different entries though they sound the same. Anything the chart cannot show is dropped.
     */
    val notes: List<SpelledNote>,
    val mode: QuizMode = QuizMode.NOTE_TO_FINGERING,
    /** Whether alternate fingerings are shown too, and count as right for their note. */
    val allFingerings: Boolean = false,
    val questionCount: Int = 10,
    /** How long a right answer stays up before the next question; zero moves on at once. */
    val successDelaySeconds: Double = 1.0,
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
        // Pitches, not written notes, as everywhere else in the quiz: D sharp and E flat are one
        // pitch and cannot be offered as wrong against each other, so together they are not a quiz.
        require(QuizQuestionGenerator.pitchCountOf(notes) >= QuizQuestionGenerator.MIN_OPTIONS) {
            "a quiz needs at least ${QuizQuestionGenerator.MIN_OPTIONS} different pitches to choose between"
        }
        require(questionCount >= 1) { "a quiz needs at least one question" }
        require(successDelaySeconds >= 0.0) { "a wait cannot be negative" }
    }
}

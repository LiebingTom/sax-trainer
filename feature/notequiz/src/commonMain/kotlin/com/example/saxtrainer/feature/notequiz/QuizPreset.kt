package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.music.Note

/**
 * A fully decided quiz: everything the setup screen would otherwise ask.
 *
 * Handing the quiz a preset means "start configured like this, and skip setup". Who decided the
 * configuration is none of the quiz's business - a lesson step, a shortcut and a "same again"
 * button all come through this same door - which is why nothing here mentions lessons.
 */
data class QuizPreset(
    /** Notes the questions are drawn from. Anything the chart cannot show is dropped. */
    val notes: List<Note>,
    val mode: QuizMode = QuizMode.NOTE_TO_FINGERING,
    val questionCount: Int = 10,
    /** How long a right answer stays up before the next question; zero moves on at once. */
    val successDelaySeconds: Double = 1.0,
    /** Whether the quiz begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) {
    init {
        require(notes.size >= QuizQuestionGenerator.MIN_OPTIONS) {
            "a quiz needs at least ${QuizQuestionGenerator.MIN_OPTIONS} notes to choose between"
        }
        require(questionCount >= 1) { "a quiz needs at least one question" }
        require(successDelaySeconds >= 0.0) { "a wait cannot be negative" }
    }
}

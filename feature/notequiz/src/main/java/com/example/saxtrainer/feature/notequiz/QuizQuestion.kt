package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.music.Note

/** Which way round a question is asked. */
enum class QuizMode(val displayName: String, val description: String) {
    NOTE_TO_FINGERING("Note to fingering", "See a note, pick the grip"),
    FINGERING_TO_NOTE("Fingering to note", "See a grip, pick the note"),
    MIXED("Mixed", "Both, in random order"),
}

/**
 * One question and its answers.
 *
 * The target is always a written note, because that is what a player reads and what the fingering
 * chart is keyed by. Which of the options is right is decided when the question is built, so
 * answering is a comparison of indices and never a second look at the chart.
 */
sealed interface QuizQuestion {
    val note: Note
    val correctIndex: Int
    val optionCount: Int

    /** The question asked in words, for the screen reader and for the result. */
    val prompt: String
}

/** A note on the staff; the options are fingerings. */
data class NoteToFingering(
    override val note: Note,
    val options: List<Fingering>,
    override val correctIndex: Int,
) : QuizQuestion {
    override val optionCount: Int get() = options.size
    override val prompt: String get() = "Which fingering is this note?"
}

/** A fingering diagram; the options are notes on the staff. */
data class FingeringToNote(
    override val note: Note,
    val fingering: Fingering,
    val options: List<Note>,
    override val correctIndex: Int,
) : QuizQuestion {
    override val optionCount: Int get() = options.size
    override val prompt: String get() = "Which note is this fingering?"
}

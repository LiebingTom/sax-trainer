package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming

/** Where the quiz is. */
sealed interface QuizPhase {
    data object Idle : QuizPhase

    /** A question on screen, waiting for an answer. */
    data class Asking(val question: QuizQuestion, val index: Int) : QuizPhase

    /**
     * The answer that was given, next to the right one.
     *
     * A phase of its own rather than a flag on [Asking]: the screen draws it differently and the
     * quiz deliberately waits here for a moment before moving on.
     */
    data class Answered(
        val question: QuizQuestion,
        val index: Int,
        val chosen: Int,
    ) : QuizPhase {
        val correct: Boolean get() = chosen == question.correctIndex
    }

    data class Finished(val result: QuizResult) : QuizPhase
}

/** One question as it turned out. */
data class AnsweredQuestion(val note: Note, val correct: Boolean)

data class QuizResult(
    val answers: List<AnsweredQuestion>,
    val plannedCount: Int,
) {
    val completed: Boolean get() = answers.size == plannedCount

    val correctCount: Int get() = answers.count { it.correct }

    /** The notes that were got wrong, each named once however often it came up. */
    val missedNotes: List<Note> get() = answers.filterNot { it.correct }.map { it.note }.distinct()
}

/**
 * Everything the quiz screen renders.
 *
 * Notes are written pitch, as everywhere a fingering is involved. Nothing here concerns sound: the
 * quiz asks whether the player recognises the relationship between a note and a grip, which is a
 * question the eyes answer.
 */
data class QuizUiState(
    val phase: QuizPhase = QuizPhase.Idle,
    val availableNotes: List<Note> = emptyList(),
    val noteSet: NoteSet = NoteSet.BEGINNER,
    val noteSets: List<NoteSet> = NoteSet.entries,
    val customNotes: Set<Note> = emptySet(),
    /** Non-null while the note dialog is open; edits stay here until they are applied. */
    val customNotesDraft: Set<Note>? = null,
    val activeNotes: List<Note> = emptyList(),
    val mode: QuizMode = QuizMode.NOTE_TO_FINGERING,
    val modes: List<QuizMode> = QuizMode.entries,
    val questionCount: Int = 10,
    val questionCounts: List<Int> = emptyList(),
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    /** True when a lesson handed the quiz a ready-made run, so there is no setup to show. */
    val isPreconfigured: Boolean = false,
) {
    val isRunning: Boolean get() = phase is QuizPhase.Asking || phase is QuizPhase.Answered

    /** A question needs something to choose between, so one note is not a quiz. */
    val canStart: Boolean get() = activeNotes.size >= QuizQuestionGenerator.MIN_OPTIONS

    val isEditingCustomNotes: Boolean get() = customNotesDraft != null

    /** How a note set reads on the setup screen. */
    fun summaryOf(set: NoteSet, chart: com.example.saxtrainer.core.fingering.FingeringChart): String =
        NoteSets.notesOf(set, chart, customNotes)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { nameOf(it) }
            ?: "No notes chosen yet"

    fun nameOf(note: Note): String = noteNaming.labelOf(note)
}

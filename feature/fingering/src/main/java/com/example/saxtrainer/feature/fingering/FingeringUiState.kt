package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.settings.NoteDisplay

/** Where the exercise is. Simpler than a long tone: the trainer works whether or not you play. */
sealed interface FingeringPhase {
    data object Idle : FingeringPhase

    data object Running : FingeringPhase

    data class Finished(val result: FingeringResult) : FingeringPhase
}

/** One note of the exercise as it turned out. */
data class PractisedNote(
    val note: Note,
    /** True when the microphone heard this note played correctly; false whenever checking was off. */
    val playedCorrectly: Boolean,
)

/**
 * What the player got through.
 *
 * With checking switched off nothing is heard, so nothing is judged: the result then simply reports
 * practice. [checked] says which of the two it is.
 */
data class FingeringResult(
    val notes: List<PractisedNote>,
    val plannedNoteCount: Int,
    val checked: Boolean,
) {
    val completed: Boolean get() = notes.size == plannedNoteCount

    val correctCount: Int get() = notes.count { it.playedCorrectly }
}

/** The note on screen right now and how far the exercise has come. */
data class FingeringProgress(
    val noteIndex: Int,
    val noteCount: Int,
    val currentNote: Note,
    val currentFingering: Fingering?,
    val nextNote: Note?,
    val remainingSeconds: Float,
    val noteSeconds: Float,
    /** How this note is presented, already resolved: never [NoteDisplay.RANDOM]. */
    val noteDisplay: NoteDisplay = NoteDisplay.BOTH,
    /** How the note after it will be presented, so the preview matches what arrives. */
    val nextNoteDisplay: NoteDisplay = NoteDisplay.BOTH,
    /** What the microphone hears, in concert pitch; null when not listening or nothing is played. */
    val detectedNote: Note? = null,
    /** True while the player is sounding the note that is shown. */
    val isCorrect: Boolean = false,
) {
    val displayPosition: Int get() = noteIndex + 1

    /** How much of the current note has elapsed, for the progress bar. */
    val noteFraction: Float
        get() = if (noteSeconds <= 0f) 0f else (1f - remainingSeconds / noteSeconds).coerceIn(0f, 1f)
}

/**
 * Everything the fingering screen renders.
 *
 * Notes are written pitch throughout, because a fingering belongs to what the player reads: a
 * written G is the same grip on every saxophone. [transposition] is only needed to know what that
 * written note should sound like when the microphone is listening.
 */
data class FingeringUiState(
    val phase: FingeringPhase = FingeringPhase.Idle,
    val availableNotes: List<Note> = emptyList(),
    val noteSet: NoteSet = NoteSet.BEGINNER,
    val noteSets: List<NoteSet> = NoteSet.entries,
    /** The player's own selection, used when [noteSet] is [NoteSet.CUSTOM]. */
    val customNotes: Set<Note> = emptySet(),
    /** Non-null while the custom notes dialog is open; edits stay here until they are applied. */
    val customNotesDraft: Set<Note>? = null,
    /** The notes the next exercise will draw from. */
    val activeNotes: List<Note> = emptyList(),
    val order: NoteOrder = NoteOrder.RANDOM,
    val secondsPerNote: Int = 5,
    /** How often the notes are worked through. */
    val rounds: Int = 1,
    val secondsOptions: List<Int> = emptyList(),
    val checkPlaying: Boolean = false,
    val checkMode: CheckMode = CheckMode.SHOW,
    /** Set when listening was asked for but the microphone could not be used. */
    val checkUnavailable: Boolean = false,
    val transposition: Transposition = Transposition.CONCERT,
    val instrumentName: String = "",
    val noteDisplay: NoteDisplay = NoteDisplay.BOTH,
    val noteDisplayOptions: List<NoteDisplay> = NoteDisplay.entries,
    val progress: FingeringProgress? = null,
    /**
     * True when the exercise was handed to the trainer ready-made, so the setup screen is not part
     * of this run and there is nothing here for the player to change.
     */
    val isPreconfigured: Boolean = false,
) {
    val isRunning: Boolean get() = phase is FingeringPhase.Running

    val canStart: Boolean get() = activeNotes.isNotEmpty()

    val isEditingCustomNotes: Boolean get() = customNotesDraft != null

    val isRandom: Boolean get() = order == NoteOrder.RANDOM

    /** How a note set reads on the setup screen. */
    fun summaryOf(set: NoteSet, chart: com.example.saxtrainer.core.fingering.FingeringChart): String =
        NoteSets.notesOf(set, chart, customNotes)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { it.label }
            ?: "No notes chosen yet"

    fun isInDraft(note: Note): Boolean = customNotesDraft?.contains(note) == true

    /** The pitch a written note should sound as on the chosen instrument. */
    fun soundingNoteOf(writtenNote: Note): Note = transposition.concertFor(writtenNote)
}

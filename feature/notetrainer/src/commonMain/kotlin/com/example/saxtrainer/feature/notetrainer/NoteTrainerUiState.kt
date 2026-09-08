package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/** Where the exercise is. */
sealed interface NoteTrainerPhase {
    data object Idle : NoteTrainerPhase

    /** Beats counted off before the first note, so the player is not caught out by it. */
    data class CountIn(val beatsRemaining: Int) : NoteTrainerPhase

    data object Running : NoteTrainerPhase

    data class Finished(val result: NoteTrainerResult) : NoteTrainerPhase
}

/** What the microphone is making of the note being asked for, right now. */
enum class Listening {
    /** Nobody is playing. */
    WAITING,

    /** A sound, but not one a pitch could be read from. Never counted against the player. */
    UNCLEAR,

    /** A note, but not the one on screen. */
    WRONG_PITCH,

    /** The note on screen, sounding. */
    CORRECT,
}

/**
 * One note as it turned out.
 *
 * [reactionSeconds] is measured from the moment the note appeared to the first moment it was heard,
 * so the wait that confirms it never inflates the number. It is null for a note that was skipped:
 * there is no honest reading for a note that was never played.
 */
data class NoteAttempt(
    val note: Note,
    val reactionSeconds: Double?,
    val wrongTries: Int,
    val skipped: Boolean,
)

data class NoteTrainerResult(
    val attempts: List<NoteAttempt>,
    val plannedNoteCount: Int,
) {
    val completed: Boolean get() = attempts.size == plannedNoteCount

    /** Notes actually played; a skipped note is what costs the score. */
    val correctCount: Int get() = attempts.count { !it.skipped }

    /**
     * The middle reaction time, not the average.
     *
     * One long think would drag a mean far enough to hide a run that was otherwise quick, and the
     * question this answers is "how am I doing", not "what was my total".
     */
    val medianReactionSeconds: Double?
        get() {
            val times = attempts.mapNotNull { it.reactionSeconds }.sorted()
            if (times.isEmpty()) return null
            val middle = times.size / 2
            return if (times.size % 2 == 1) {
                times[middle]
            } else {
                (times[middle - 1] + times[middle]) / 2
            }
        }

    /** How many notes were played inside the target the player set. */
    fun withinTarget(targetSeconds: Int): Int =
        attempts.count { it.reactionSeconds != null && it.reactionSeconds <= targetSeconds }
}

/** The note on screen right now and how far the exercise has come. */
data class NoteTrainerProgress(
    val noteIndex: Int,
    val noteCount: Int,
    val currentNote: Note,
    /** Only when the hint is on; the fingering is the answer, not the question. */
    val fingering: Fingering? = null,
    val listening: Listening = Listening.WAITING,
    /** What the microphone hears, as the player would write it; null when nothing is heard. */
    val detectedNote: Note? = null,
    val wrongTries: Int = 0,
    /** Filled while the note is being shown as played, so the screen can say how quick it was. */
    val lastReactionSeconds: Double? = null,
    /**
     * How far through the hold the note being sounded is, or null when there is no hold to be
     * through: nothing right is sounding, or the exercise advances as soon as the note is right.
     */
    val holdFraction: Float? = null,
    /** True while the note is being shown as done, between the hold finishing and the next note. */
    val landed: Boolean = false,
) {
    val displayPosition: Int get() = noteIndex + 1

    val isCorrect: Boolean get() = listening == Listening.CORRECT

    /** The right note is sounding but the hold is not finished: the moment to keep it going. */
    val isHolding: Boolean get() = isCorrect && !landed && holdFraction != null
}

/**
 * Everything the note trainer screen renders.
 *
 * Notes are written pitch: the player reads a written note and fingers it, and only the comparison
 * with the microphone happens in concert pitch. The challenge is always the notation - there is
 * deliberately no setting that could replace it with a note name, because reading it is the
 * exercise. [showNoteName] adds the name underneath for anyone who wants the crutch.
 */
data class NoteTrainerUiState(
    val phase: NoteTrainerPhase = NoteTrainerPhase.Idle,
    val availableNotes: List<Note> = emptyList(),
    val noteSet: NoteSet = NoteSet.BEGINNER,
    val noteSets: List<NoteSet> = NoteSet.entries,
    val customNotes: Set<Note> = emptySet(),
    /** Non-null while the note dialog is open; edits stay here until they are applied. */
    val customNotesDraft: Set<Note>? = null,
    val activeNotes: List<Note> = emptyList(),
    val order: NoteOrder = NoteOrder.RANDOM,
    val rounds: Int = 1,
    val roundOptions: List<Int> = listOf(1, 2, 3),
    /** Whether the fingering is offered while the note is being asked for. Off by default. */
    val showFingeringHint: Boolean = false,
    /** Whether the note's name is shown under the staff. Off by default; the notation is the point. */
    val showNoteName: Boolean = false,
    /** What counts as a quick answer. Scoring only - a slow note is never a failed one. */
    val targetSeconds: Int = 4,
    val targetOptions: List<Int> = emptyList(),
    /**
     * How long the right note has to be kept sounding before the exercise moves on.
     *
     * Zero is "until it is right": the note is confirmed and gone, which is what the trainer has
     * always done. Anything above it asks the player to hold the note, and a note that stops or
     * changes in the middle starts its hold again from nothing.
     */
    /** How long a right note has to be kept, in beats; zero moves on as soon as it is right. */
    val holdBeats: Beats = Beats.ZERO,
    /** The speed those beats are counted at, which is what gives them a length in seconds. */
    val tempo: Tempo = Tempo(60),
    val tempoRange: IntRange = Tempo.SUPPORTED_RANGE,
    val holdOptions: List<Beats> = emptyList(),
    /** Set when the microphone was asked for and refused; without one there is no exercise. */
    val microphoneUnavailable: Boolean = false,
    val isPaused: Boolean = false,
    val countInBeats: Int = AppSettings.DEFAULT_COUNT_IN_BEATS,
    val countInClick: Boolean = true,
    val transposition: Transposition = Transposition.CONCERT,
    val instrumentName: String = "",
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val progress: NoteTrainerProgress? = null,
    val isPreconfigured: Boolean = false,
) {
    val isRunning: Boolean get() = phase is NoteTrainerPhase.Running

    /** Running or about to be: either way the setup is no longer the player's to change. */
    val isBusy: Boolean get() = isRunning || phase is NoteTrainerPhase.CountIn

    /**
     * Reading a note and playing it needs a microphone. Without one there is nothing to check, so
     * the exercise says so instead of pretending.
     */
    val canStart: Boolean get() = activeNotes.isNotEmpty() && !microphoneUnavailable

    val isEditingCustomNotes: Boolean get() = customNotesDraft != null

    val isRandom: Boolean get() = order == NoteOrder.RANDOM

    /** No hold to keep: the exercise advances as soon as the note is right. */
    val holdsUntilCorrect: Boolean get() = holdBeats <= Beats.ZERO

    /** How a note set reads on the setup screen. */
    fun summaryOf(set: NoteSet, chart: com.example.saxtrainer.core.fingering.FingeringChart): String =
        NoteSets.notesOf(set, chart, customNotes)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { nameOf(it) }
            ?: "No notes chosen yet"

    fun nameOf(note: Note): String = noteNaming.labelOf(note)

    /** The pitch a written note should sound as on the chosen instrument. */
    fun soundingNoteOf(writtenNote: Note): Note = transposition.concertFor(writtenNote)

    /** What a heard concert pitch would be written as, so the screen speaks the player's language. */
    fun writtenNoteOf(concertNote: Note): Note = transposition.writtenFor(concertNote)
}

package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/** Where the exercise is. Simpler than a long tone: the trainer works whether or not you play. */
sealed interface FingeringPhase {
    data object Idle : FingeringPhase

    /** Beats counted off before the first note, so the player is not caught out by it. */
    data class CountIn(val beatsRemaining: Int) : FingeringPhase

    data object Running : FingeringPhase

    data class Finished(val result: FingeringResult) : FingeringPhase
}

/** What the microphone is making of the note being asked for, right now. */
enum class Heard {
    /** Nobody is playing. */
    NOTHING,

    /** A sound, but not one a pitch could be read from. Never counted against the player. */
    UNCLEAR,

    /** A note, but not the one on screen. */
    WRONG,

    /** The note on screen, sounding. */
    CORRECT,
}

/** One note of the exercise as it turned out. */
data class PractisedNote(
    val note: Note,
    /** True when the microphone heard this note played; false whenever checking was off. */
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
    val noteDisplay: NoteDisplay = NoteDisplay.STAFF,
    /** How the note after it will be presented, so the preview matches what arrives. */
    /**
     * How the note after it will be presented.
     *
     * Kept because the schedule produces it, but no longer shown: a next note is always given as
     * its name, so that reading ahead by letter cannot replace reading the staff.
     */
    val nextNoteDisplay: NoteDisplay = NoteDisplay.STAFF,
    /**
     * Whether the fingering is on screen.
     *
     * With checking off it always is - the diagram is the lesson. With checking on it is the
     * answer, so it is held back until the note has been played or its time is up, and recalling
     * the grip is what the exercise is actually asking for.
     */
    val showFingering: Boolean = true,
    val heard: Heard = Heard.NOTHING,
    /** What the microphone hears, as the player would write it; null when nothing is heard. */
    val detectedNote: Note? = null,
    /**
     * True once the note has been played right and the trainer is only waiting before moving on.
     *
     * The wait is the player's to cut short: while this holds, the exercise panel is a button that
     * goes straight to the next note. What was heard afterwards no longer matters, so the correct
     * feedback stays up whether or not the note is still sounding.
     */
    val landed: Boolean = false,
) {
    val displayPosition: Int get() = noteIndex + 1

    val isCorrect: Boolean get() = heard == Heard.CORRECT || landed

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
    /** How long each note is worked on. Beats, turned into seconds against [tempo]. */
    val beatsPerNote: Beats = Beats.of(5),
    /** The speed those beats are counted at, which is what gives them a length in seconds. */
    val tempo: Tempo = Tempo(60),
    val tempoRange: IntRange = Tempo.SUPPORTED_RANGE,
    /**
     * Whether the note after this one is shown.
     *
     * Turning it off is a way to practise: the next note is always given as its name, so leaving it
     * on lets a player read letters instead of notation. Off, there is nothing to read but the
     * staff.
     */
    val showNextNote: Boolean = true,
    /** How long a correctly played note is left on screen before the next one, zero for no wait. */
    val successDelaySeconds: Float = 1f,
    val successDelayOptions: List<Float> = emptyList(),
    /** How often the notes are worked through. */
    val rounds: Int = 1,
    val beatOptions: List<Beats> = emptyList(),
    /** Whether the player has to play each note, or only look at its fingering. */
    val checkPlaying: Boolean = false,
    /** Set when listening was asked for but the microphone could not be used. */
    val checkUnavailable: Boolean = false,
    val transposition: Transposition = Transposition.CONCERT,
    val instrumentName: String = "",
    /** What the note letters are called; a display convention, nothing else. */
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val noteDisplay: NoteDisplay = NoteDisplay.STAFF,
    val noteDisplayOptions: List<NoteDisplay> = NoteDisplay.entries,
    val progress: FingeringProgress? = null,
    /**
     * True while the run is held. The exercise keeps its place: nothing is judged, nothing advances,
     * and no result is published until it is either continued or stopped.
     */
    val isPaused: Boolean = false,
    /** Beats counted off before the first note; taken from the app settings, zero for none. */
    val countInBeats: Int = AppSettings.DEFAULT_COUNT_IN_BEATS,
    /** Whether that count is clicked out loud, also from the app settings. */
    val countInClick: Boolean = true,
    /**
     * True when the exercise was handed to the trainer ready-made, so the setup screen is not part
     * of this run and there is nothing here for the player to change.
     */
    val isPreconfigured: Boolean = false,
) {
    val isRunning: Boolean get() = phase is FingeringPhase.Running

    /** Running or about to be: either way the setup is no longer the player's to change. */
    val isBusy: Boolean get() = isRunning || phase is FingeringPhase.CountIn

    val canStart: Boolean get() = activeNotes.isNotEmpty()

    val isEditingCustomNotes: Boolean get() = customNotesDraft != null

    val isRandom: Boolean get() = order == NoteOrder.RANDOM

    /** How a note set reads on the setup screen. */
    fun summaryOf(set: NoteSet, chart: com.example.saxtrainer.core.fingering.FingeringChart): String =
        NoteSets.notesOf(set, chart, customNotes)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { nameOf(it) }
            ?: "No notes chosen yet"

    fun isInDraft(note: Note): Boolean = customNotesDraft?.contains(note) == true

    /** How a note is written on screen, under the chosen naming. */
    fun nameOf(note: Note): String = noteNaming.labelOf(note)

    /** The pitch a written note should sound as on the chosen instrument. */
    fun soundingNoteOf(writtenNote: Note): Note = transposition.concertFor(writtenNote)

    /** What a heard concert pitch would be written as, so the screen speaks the player's language. */
    fun writtenNoteOf(concertNote: Note): Note = transposition.writtenFor(concertNote)
}

package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.PracticeUnit
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.AppSettings

/** Where the exercise is. */
sealed interface PhraseTrainerPhase {
    data object Idle : PhraseTrainerPhase

    /** Beats counted off before the first unit, so the player is not caught out by it. */
    data class CountIn(val beatsRemaining: Int) : PhraseTrainerPhase

    /** One unit shown in full, before it is taken away and has to be played from memory. */
    data class Showing(val unitIndex: Int) : PhraseTrainerPhase

    /** A unit hidden, being played note by note; see [PhraseTrainerUiState.progress] for detail. */
    data object Listening : PhraseTrainerPhase

    data class Finished(val result: PhraseTrainerResult) : PhraseTrainerPhase
}

/** What the microphone is making of the note being asked for, right now. */
enum class Listening {
    /** Nobody is playing. */
    WAITING,

    /** A sound, but not one a pitch could be read from. Never counted against the player. */
    UNCLEAR,

    /** A note, but not the one expected. */
    WRONG_PITCH,

    /** The expected note, sounding. */
    CORRECT,
}

/** One note of a unit as it turned out: played, or given up on. */
data class StepAttempt(val note: SpelledNote?, val played: Boolean)

/** One practice unit as it turned out: every one of its notes has to have been played. */
data class UnitAttempt(val steps: List<StepAttempt>) {
    val correct: Boolean get() = steps.all { it.played }
}

data class PhraseTrainerResult(val attempts: List<UnitAttempt>, val plannedCount: Int) {
    val completed: Boolean get() = attempts.size == plannedCount
    val correctCount: Int get() = attempts.count { it.correct }
}

/** The note being asked for right now and how far the exercise has come. */
data class PhraseTrainerProgress(
    val unitIndex: Int,
    val unitCount: Int,
    /** Which note of the unit is asked for, counting notes only: a rest is not played. */
    val stepIndex: Int,
    /** How many notes the unit has to play, its rests left out. */
    val stepCount: Int,
    /** Null while the current step is a rest: nothing to play, nothing to listen for. */
    /** As the phrase writes it. */
    val currentNote: SpelledNote?,
    val listening: Listening = Listening.WAITING,
    /**
     * What the microphone hears, as the player would write it; null when nothing is heard. Heard as
     * a pitch, so written as the phrase would write it - see the view model's `playUnit`.
     */
    val detectedNote: SpelledNote? = null,
    /** True while the note is being shown as landed, between confirmation and the next note. */
    val landed: Boolean = false,
) {
    val isCorrect: Boolean get() = listening == Listening.CORRECT
}

/**
 * Everything the phrase trainer screen renders.
 *
 * [phrase] is derived rather than stored: a library phrase is looked up by id, a scale pattern is a
 * stored [PhraseRecipe] resolved against whichever scale, root and octave were picked. Either way
 * how much is shown at once - [Phrase.trainingBars] - is the content's own to say, never a choice
 * offered here: there is deliberately no grouping picker. A preset overrides all of that with
 * [presetPhrase] instead.
 */
data class PhraseTrainerUiState(
    val phase: PhraseTrainerPhase = PhraseTrainerPhase.Idle,
    val sourceType: PhraseSourceType = PhraseSourceType.LIBRARY,
    val library: List<Phrase> = emptyList(),
    val libraryPhraseId: String? = null,
    /** Whether the phrase-library window is open, listing [library] for the player to choose from. */
    val libraryPickerOpen: Boolean = false,
    val scaleChoices: List<ScaleChoice> = ScaleChoice.all,
    val scaleChoiceId: String = ScaleChoice.default?.id.orEmpty(),
    /**
     * The octaves a scale pattern is generated in - a setting of its own, apart from which key it
     * is. More than one means a training unit can come from any of them, not that they are woven
     * together - see [PhraseRecipe.resolve].
     */
    val octaves: Set<Int> = setOf(DEFAULT_OCTAVE),
    val patterns: List<PhraseRecipe> = emptyList(),
    val patternId: String? = null,
    /** Whether the scale-pattern window is open: octave, scale and the stored patterns to choose. */
    val patternPickerOpen: Boolean = false,
    val showSeconds: Double = 2.5,
    /** How many times through the whole run - see [com.example.saxtrainer.core.phrase.repeated]. */
    val repeats: Int = 1,
    val repeatRange: IntRange = 1..12,
    /**
     * Whether every repeat's units are drawn from one shuffled pool instead of being walked one
     * whole pass after another - see [com.example.saxtrainer.core.phrase.repeated].
     */
    val mixed: Boolean = false,
    /** Whether a note also has to be held for its written length - see [PhraseTrainerPreset]. */
    val checkDuration: Boolean = false,
    val tempo: Tempo = Tempo(80),
    val microphoneUnavailable: Boolean = false,
    val isPaused: Boolean = false,
    val countInBeats: Int = AppSettings.DEFAULT_COUNT_IN_BEATS,
    val countInClick: Boolean = true,
    val transposition: Transposition = Transposition.CONCERT,
    val instrumentName: String = "",
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val octaveNumbering: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    val progress: PhraseTrainerProgress? = null,
    /**
     * The units of the run being played, in the order they come: [units] repeated, and shuffled
     * when [mixed]. What a unit index in [PhraseTrainerPhase.Showing] or [progress] counts into -
     * not [units], which is one pass through the phrase in order.
     */
    val runUnits: List<PracticeUnit> = emptyList(),
    /** Set only by a preset that is not being edited: the phrase to run, bypassing setup entirely. */
    val presetPhrase: Phrase? = null,
    /** True when a lesson handed the trainer a ready-made run, so there is no setup to show. */
    val isPreconfigured: Boolean = false,
) {
    val isRunning: Boolean get() = phase is PhraseTrainerPhase.Showing || phase is PhraseTrainerPhase.Listening

    /** Running or about to be: either way the setup is no longer the player's to change. */
    val isBusy: Boolean get() = isRunning || phase is PhraseTrainerPhase.CountIn

    /** Whether the unit stays on screen while it is played rather than being taken away. */
    val showsThroughout: Boolean get() = showSeconds.isInfinite()

    val scaleChoice: ScaleChoice? get() = scaleChoices.firstOrNull { it.id == scaleChoiceId }
    val pattern: PhraseRecipe? get() = patterns.firstOrNull { it.id == patternId }

    /** The phrase this setup currently describes, however it was chosen. */
    val phrase: Phrase?
        get() = presetPhrase ?: when (sourceType) {
            PhraseSourceType.LIBRARY -> libraryPhraseId?.let { id -> library.firstOrNull { it.id == id } }
            PhraseSourceType.SCALE_PATTERN -> {
                val choice = scaleChoice
                val recipe = pattern
                if (choice != null && recipe != null && octaves.isNotEmpty()) {
                    recipe.resolve(choice.scale, choice.root, octaves)
                } else {
                    null
                }
            }
        }

    /** The practice units the current phrase is walked in - a unit is one or more bars wide. */
    val units: List<PracticeUnit> get() = phrase?.units.orEmpty()

    /**
     * How many units the whole run has: [units] repeated [repeats] times.
     *
     * A count only - which units land where is decided once, when the run actually starts, since
     * [mixed] shuffles them and a setup screen must not reshuffle every time it is read.
     */
    val plannedUnitCount: Int get() = units.size * repeats

    /** Playing a phrase needs a microphone; without one there is nothing to check. */
    val canStart: Boolean get() = phrase != null && !microphoneUnavailable

    /** The name of a written note, as written, under the chosen naming. */
    fun nameOf(note: SpelledNote): String = noteNaming.labelOf(note, octaveNumbering)

    /** The pitch a written note should sound as on the chosen instrument. */
    fun soundingNoteOf(writtenNote: Note): Note = transposition.concertFor(writtenNote)

    /** What a heard concert pitch would be written as, so the screen speaks the player's language. */
    fun writtenNoteOf(concertNote: Note): Note = transposition.writtenFor(concertNote)

    companion object {
        /**
         * Octaves offered on the setup screen.
         *
         * Starts at 4, not lower: written octave 3 is below the saxophone family's lowest note
         * (low Bb, A#3) for every root this app offers, so it could never be played - offering it
         * would be a choice with no honest answer.
         */
        val OCTAVE_RANGE: IntRange = 4..6
        val DEFAULT_OCTAVE: Int = 4

        /**
         * The last step of the show time: the unit never disappears, it stays up while it is
         * played. Infinite rather than a flag of its own, so it is simply the far end of the same
         * slider and is written down like any other show time.
         */
        const val SHOW_THROUGHOUT: Double = Double.POSITIVE_INFINITY

        /**
         * How long a unit may be shown before it disappears: half-second steps up to five seconds,
         * then [SHOW_THROUGHOUT].
         */
        val SHOW_SECONDS_OPTIONS: List<Double> = (1..10).map { it * 0.5 } + SHOW_THROUGHOUT
        /** The timed steps' range; [SHOW_THROUGHOUT] stands apart from it. */
        val SHOW_SECONDS_RANGE: ClosedFloatingPointRange<Double> =
            SHOW_SECONDS_OPTIONS.first()..SHOW_SECONDS_OPTIONS.last { it.isFinite() }
    }
}

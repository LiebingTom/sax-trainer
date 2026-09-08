package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.exercise.ExercisePattern
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.settings.NoteDisplay

/**
 * Where the exercise currently is.
 *
 * [Running] ends when the attempt has lasted the target duration in audio time, whether or not a
 * tone was actually heard: the target duration is the length of the *attempt*, not an amount of
 * validated playing. `LongToneProcessor` already accumulates the gated-on portion as
 * `soundingSeconds` and now also measures pitch, so completing on validated held time instead is a
 * change to the completion condition in [LongToneViewModel] alone.
 */
sealed interface LongTonePhase {
    data object Idle : LongTonePhase

    /** Beats still to be counted before the first note; this exercise counts itself in a bar. */
    data class Countdown(val beatsRemaining: Int) : LongTonePhase

    data object Running : LongTonePhase

    data class Finished(val result: LongToneResult) : LongTonePhase

    data class Failed(val error: AudioCaptureError) : LongTonePhase
}

/**
 * Summary shown after an exercise.
 *
 * [heldSeconds] is the length of the attempt in audio time; [soundingSeconds] is how much of it was
 * above the signal gate. The pitch figures describe how the note was tuned and how steadily it was
 * held, but they do not decide completion - see [LongTonePhase].
 */
data class LongToneResult(
    /** One entry per note that was played, in the order they were practised. */
    val steps: List<LongToneStepResult>,
    val heldSeconds: Float,
    val targetSeconds: Float,
    val soundingSeconds: Float,
    val averageLevelDbfs: Float,
    val steadyFraction: Float,
    val completed: Boolean,
    /** Mean deviation from the target notes over all accepted estimates; null if there were none. */
    val averagePitchCents: Float? = null,
    /** Spread of those deviations around their mean - how steady the pitch was, not how correct. */
    val pitchStabilityCents: Float? = null,
)

/** How one note of the exercise went. */
data class LongToneStepResult(
    val note: Note,
    val targetBeats: Beats,
    val heldSeconds: Float,
    val soundingSeconds: Float,
    val averageLevelDbfs: Float,
    val steadyFraction: Float,
    val averagePitchCents: Float?,
    val pitchStabilityCents: Float?,
    val completed: Boolean,
)

/**
 * Pitch of the current frame, present only while a note is actually being heard with enough
 * confidence. A null pitch means "nothing reliable to show", never a stale or guessed value.
 */
data class PitchMeasurement(
    val frequencyHz: Float,
    /** Deviation from the target note, positive when sharp. */
    val cents: Float,
    val confidence: Float,
    val detectedNote: Note,
    /** True when the deviation is so large that another note is being played, not a mistuned one. */
    val offTarget: Boolean,
)

/** Live measurements, refreshed at the UI update rate rather than per audio frame. */
data class LongToneMeasurements(
    val rmsDbfs: Float = -120f,
    val peakDbfs: Float = -120f,
    val levelFraction: Float = 0f,
    val signalPresent: Boolean = false,
    /** Magnitudes folded into display bands, already limited to the displayed frequency range. */
    val spectrumBands: FloatArray = FloatArray(0),
    val displayMaxFrequencyHz: Float = 0f,
    /** Null while no reliable pitch is available, which the UI shows as a neutral state. */
    val pitch: PitchMeasurement? = null,
) {
    // Array field: identity comparison would be wrong for a state class, so compare contents.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LongToneMeasurements) return false
        return rmsDbfs == other.rmsDbfs &&
            peakDbfs == other.peakDbfs &&
            levelFraction == other.levelFraction &&
            signalPresent == other.signalPresent &&
            displayMaxFrequencyHz == other.displayMaxFrequencyHz &&
            pitch == other.pitch &&
            spectrumBands.contentEquals(other.spectrumBands)
    }

    override fun hashCode(): Int {
        var result = rmsDbfs.hashCode()
        result = 31 * result + peakDbfs.hashCode()
        result = 31 * result + levelFraction.hashCode()
        result = 31 * result + signalPresent.hashCode()
        result = 31 * result + displayMaxFrequencyHz.hashCode()
        result = 31 * result + (pitch?.hashCode() ?: 0)
        result = 31 * result + spectrumBands.contentHashCode()
        return result
    }
}

/**
 * Everything the long tone screen renders. Notes are stored as concert pitch and only labelled
 * through [transposition], so a change of instrument never changes what is measured.
 */
data class ExerciseSelection(
    val pattern: ExercisePattern,
    val rootNote: Note,
    val beatsPerNote: Beats,
    val tempo: Tempo,
) {
    /** The notes this exercise will ask for, in concert pitch. */
    val notes: List<Note> get() = pattern.notesFrom(rootNote)

    val secondsPerNote: Float get() = tempo.secondsFor(beatsPerNote).toFloat()

    val totalSeconds: Float get() = secondsPerNote * pattern.noteCount
}

/** Where the player is in the exercise while it runs. */
data class ExerciseProgress(
    val noteIndex: Int,
    val noteCount: Int,
    val currentNote: Note,
    val upcomingNotes: List<Note>,
    val noteElapsedSeconds: Float,
    val noteTargetSeconds: Float,
    val exerciseElapsedSeconds: Float,
    val exerciseTotalSeconds: Float,
    /** How this note is presented, already resolved: never [NoteDisplay.RANDOM]. */
    val noteDisplay: NoteDisplay = NoteDisplay.STAFF,
    /**
     * How the note after it will be presented.
     *
     * Kept because the schedule produces it, but no longer shown: a next note is always given as
     * its name, so that reading ahead by letter cannot replace reading the staff.
     */
    val nextNoteDisplay: NoteDisplay = NoteDisplay.STAFF,
) {
    val displayPosition: Int get() = noteIndex + 1

    val noteFraction: Float
        get() = if (noteTargetSeconds <= 0f) 0f else (noteElapsedSeconds / noteTargetSeconds).coerceIn(0f, 1f)

    val exerciseFraction: Float
        get() = if (exerciseTotalSeconds <= 0f) {
            0f
        } else {
            (exerciseElapsedSeconds / exerciseTotalSeconds).coerceIn(0f, 1f)
        }
}

/**
 * Everything the long tone screen renders. Notes are stored as concert pitch and only labelled
 * through [transposition], so a change of instrument never changes what is measured.
 */
data class LongToneUiState(
    val phase: LongTonePhase = LongTonePhase.Idle,
    val selection: ExerciseSelection,
    val patterns: List<ExercisePattern>,
    val selectableNotes: List<Note>,
    val beatOptions: List<Beats>,
    val tempoRange: IntRange,
    val transposition: Transposition,
    val instrumentName: String,
    /** What the note letters are called; a display convention, nothing else. */
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val noteDisplay: NoteDisplay = NoteDisplay.STAFF,
    val noteDisplayOptions: List<NoteDisplay> = NoteDisplay.entries,
    /**
     * Whether the note after this one is shown.
     *
     * Turning it off is a way to practise: the next note is always given as its name, so leaving it
     * on lets a player read letters instead of notation. Off, there is nothing to read but the
     * staff.
     */
    val showNextNote: Boolean = true,
    val progress: ExerciseProgress? = null,
    val measurements: LongToneMeasurements = LongToneMeasurements(),
    /**
     * True while the attempt is held. Audio time is the exercise's clock, so a paused exercise is
     * simply one that analyses nothing: the note keeps the seconds it had, and neither the tone the
     * player is not playing nor the silence counts against them.
     */
    val isPaused: Boolean = false,
    /** Whether the count-in is clicked out loud; from the app settings. */
    val countInClick: Boolean = true,
    /** Half width of the cents meter, so the UI does not decide the scale itself. */
    val centsDisplayRange: Float = 50f,
    /**
     * True when the exercise arrived ready-made rather than being set up here.
     *
     * The setup screen is then skipped: what to practise has already been decided, and offering to
     * change it would only invite the player to undo somebody else's lesson by accident.
     */
    val isPreconfigured: Boolean = false,
) {
    val isBusy: Boolean get() = phase is LongTonePhase.Running || phase is LongTonePhase.Countdown

    /** Note name as the player reads it on their instrument, under the chosen naming. */
    fun writtenLabel(note: Note): String = noteNaming.labelOf(transposition.writtenFor(note))

    /** A sounding pitch, named the same way. */
    fun soundingLabel(note: Note): String = noteNaming.labelOf(note)

    /** The note the player should be sounding right now, or the first one before the start. */
    val currentNote: Note get() = progress?.currentNote ?: selection.notes.first()
}

package com.example.saxtrainer.feature.sequence

import com.example.saxtrainer.core.exercise.NoteTiming
import com.example.saxtrainer.core.exercise.SequenceDefinition
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.Transposition

/** Where the exercise is. There is no failure phase: the clock finishes whatever the player does. */
sealed interface SequencePhase {
    data object Idle : SequencePhase

    /** Clicks before the first note, so the player knows the speed before having to play. */
    data class CountIn(val beatsRemaining: Int) : SequencePhase

    data object Running : SequencePhase

    data class Finished(val result: SequenceResult) : SequencePhase
}

/**
 * How one note of the sequence turned out.
 *
 * There is no attempt for a rest. A silence is not something a player gets right or wrong, so it is
 * not graded at all - which is why [index] is carried explicitly: it numbers the step in the whole
 * sequence, and rests leave gaps in the numbering.
 *
 * Pitch and timing are separate answers to separate questions, and either can be null because the
 * check was switched off - which is not the same as failing it. "Right note, late" is a normal thing
 * to be told, and so is "nothing was measured".
 */
data class NoteAttempt(
    val index: Int,
    val note: Note,
    /** True when the written note was heard at all; null when nobody was listening. */
    val pitchCorrect: Boolean?,
    /** null when timing was not being checked. */
    val timing: NoteTiming?,
)

data class SequenceResult(
    val sequenceName: String,
    /** One per note reached, rests left out; [NoteAttempt.index] says which step each one was. */
    val attempts: List<NoteAttempt>,
    /** Notes the sequence asks for, rests not counted: nobody plays a rest. */
    val plannedNoteCount: Int,
    /** True when the sequence ran to its end, rather than being stopped part way through. */
    val completed: Boolean,
    val checkedPitch: Boolean,
    val checkedTiming: Boolean,
) {

    val correctCount: Int get() = attempts.count { it.pitchCorrect == true }

    val onTimeCount: Int get() = attempts.count { it.timing == NoteTiming.ON_TIME }
}

/** The note being played now and what has happened so far. */
data class SequenceProgress(
    val noteIndex: Int,
    val noteCount: Int,
    /** Which time through, counted from zero, and how many times in all. */
    val passIndex: Int,
    val passCount: Int,
    /** The note being played, or null while the sequence is resting. */
    val currentNote: Note?,
    val elapsedBeats: Beats,
    val totalBeats: Beats,
    /** Where the note on screen starts and ends, so its own share of the run can be drawn. */
    val noteStartBeats: Beats = Beats.ZERO,
    val noteEndBeats: Beats = Beats.ZERO,
    /** What the microphone hears, as the player would write it; null when not listening. */
    val detectedNote: Note? = null,
    /** True while the note on screen is the note being sounded. */
    val isCorrect: Boolean = false,
    /** Notes already heard correctly, by index, so the staff can colour what is behind the cursor. */
    val playedCorrectly: Set<Int> = emptySet(),
) {
    val displayPosition: Int get() = noteIndex + 1

    val displayPass: Int get() = passIndex + 1

    /** How much of the note on screen has gone by. */
    val noteFraction: Float
        get() {
            val length = noteEndBeats.value - noteStartBeats.value
            if (length <= 0.0) return 0f
            return ((elapsedBeats.value - noteStartBeats.value) / length)
                .coerceIn(0.0, 1.0)
                .toFloat()
        }

    val fraction: Float
        get() = if (totalBeats.value <= 0.0) {
            0f
        } else {
            (elapsedBeats.value / totalBeats.value).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * Everything the sequence screen renders.
 *
 * Notes are **written** pitch throughout - what the player reads - because a scale is a scale on
 * whatever horn it is played. [transposition] is needed only to know what a written note should
 * sound like when the microphone is listening.
 */
data class SequenceUiState(
    val phase: SequencePhase = SequencePhase.Idle,
    val library: List<SequenceDefinition> = emptyList(),
    val selectedId: String = "",
    val sequence: ToneSequence,
    val tempo: Tempo = Tempo(80),
    val tempoRange: IntRange = Tempo.SUPPORTED_RANGE,
    /** Play the notes back down to where they started. */
    val addInverse: Boolean = false,
    /** How many times through the whole thing. */
    val repeats: Int = 1,
    val repeatRange: IntRange = 1..12,
    val metronome: Boolean = false,
    /** False when there is no audio output to click with, in which case the switch is not offered. */
    val metronomeAvailable: Boolean = true,
    val checkPitch: Boolean = false,
    val checkTiming: Boolean = false,
    /** Set when listening was asked for but the microphone could not be used. */
    val checkUnavailable: Boolean = false,
    val transposition: Transposition = Transposition.CONCERT,
    val instrumentName: String = "",
    /** What the note letters are called; a display convention, nothing else. */
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val octaveNumbering: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    /** True when the exercise was handed over ready-made, so there is no setup to show. */
    val isPreconfigured: Boolean = false,
    val progress: SequenceProgress? = null,
) {
    val isRunning: Boolean get() = phase is SequencePhase.Running || phase is SequencePhase.CountIn

    val selected: SequenceDefinition? get() = library.firstOrNull { it.id == selectedId }

    /** Notes in one time through, which is what [SequenceProgress.passIndex] counts. */
    val notesPerPass: Int get() = sequence.steps.size / repeats

    val canStart: Boolean get() = selected != null

    /**
     * How a note reads on screen: named as the player has chosen and spelled as the music asks, so
     * F major shows a B flat - "B♭" or, in German, "B" - and never an A sharp.
     */
    fun nameOf(note: Note): String = noteNaming.labelOf(note, sequence.accidentalStyle, octaveNumbering)

    /** The pitch a written note should sound as on the chosen instrument. */
    fun soundingNoteOf(writtenNote: Note): Note = transposition.concertFor(writtenNote)

    /** What the microphone heard, written as the player would read it. */
    fun writtenNoteOf(concertNote: Note): Note = transposition.writtenFor(concertNote)
}

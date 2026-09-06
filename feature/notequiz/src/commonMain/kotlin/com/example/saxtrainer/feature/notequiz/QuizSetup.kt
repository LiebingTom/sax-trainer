package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How this quiz's setup is written down and read back.
 *
 * Only what the setup screen asks for. Notes travel as MIDI numbers: the one way of writing a pitch
 * that no display setting, naming convention or transposition can change.
 */
internal fun QuizUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_NOTE_SET, noteSet)
    putInts(KEY_CUSTOM_NOTES, customNotes.map { it.midiNumber }.sorted())
    put(KEY_MODE, mode)
    put(KEY_QUESTION_COUNT, questionCount)
    put(KEY_SUCCESS_DELAY, successDelaySeconds)
}

/**
 * The same setup, offered back as the state to open on.
 *
 * [chart] filters the remembered notes rather than being trusted to agree with them: it says what
 * this build can ask about at all, and the setup only says what was chosen last time.
 */
internal fun QuizUiState.withSetup(setup: ExerciseSetup, chart: FingeringChart): QuizUiState {
    if (setup.isEmpty) return this

    val remembered = setup.ints(KEY_CUSTOM_NOTES)
        ?.map(Note::fromMidi)
        ?.filter(chart::supports)
        ?.toSet()
    val custom = remembered ?: customNotes
    val noteSet = setup.enum(KEY_NOTE_SET, NoteSet.entries, this.noteSet)

    return copy(
        noteSet = noteSet,
        customNotes = custom,
        activeNotes = NoteSets.notesOf(noteSet, chart, custom),
        mode = setup.enum(KEY_MODE, QuizMode.entries, mode),
        questionCount = setup.int(KEY_QUESTION_COUNT, questionCount).coerceAtLeast(1),
        successDelaySeconds = setup.double(KEY_SUCCESS_DELAY, successDelaySeconds)
            .coerceAtLeast(0.0),
    )
}

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_MODE = "mode"
private const val KEY_QUESTION_COUNT = "question_count"
private const val KEY_SUCCESS_DELAY = "success_delay"

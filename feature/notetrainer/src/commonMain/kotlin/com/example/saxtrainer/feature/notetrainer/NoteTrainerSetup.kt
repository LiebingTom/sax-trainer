package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How this trainer's setup is written down and read back.
 *
 * Only what the setup screen asks for. Anything the app decides for every tool - the instrument,
 * what notes are called, the count-in - is settings and is already kept; copying it here would give
 * it a second home and a chance to disagree with the first.
 *
 * Notes travel as MIDI numbers because that is the one way of writing a pitch that no display
 * setting, naming convention or transposition can change.
 */
internal fun NoteTrainerUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_NOTE_SET, noteSet)
    putInts(KEY_CUSTOM_NOTES, customNotes.map { it.midiNumber }.sorted())
    put(KEY_ORDER, order)
    put(KEY_ROUNDS, rounds)
    put(KEY_FINGERING_HINT, showFingeringHint)
    put(KEY_NOTE_NAME, showNoteName)
    put(KEY_TARGET_SECONDS, targetSeconds)
    put(KEY_HOLD_SECONDS, holdSeconds)
}

/**
 * The same setup, offered back as the state to open on.
 *
 * [chart] decides which notes the trainer can ask for at all, so a remembered choice is filtered
 * through it rather than trusted: the chart is what a build of the app supports, and the setup is
 * only what was chosen last time.
 */
internal fun NoteTrainerUiState.withSetup(
    setup: ExerciseSetup,
    chart: FingeringChart,
): NoteTrainerUiState {
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
        order = setup.enum(KEY_ORDER, NoteOrder.entries, order),
        rounds = setup.int(KEY_ROUNDS, rounds).coerceAtLeast(1),
        showFingeringHint = setup.boolean(KEY_FINGERING_HINT, showFingeringHint),
        showNoteName = setup.boolean(KEY_NOTE_NAME, showNoteName),
        targetSeconds = setup.int(KEY_TARGET_SECONDS, targetSeconds),
        holdSeconds = setup.double(KEY_HOLD_SECONDS, holdSeconds).coerceAtLeast(0.0),
    )
}

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_ORDER = "order"
private const val KEY_ROUNDS = "rounds"
private const val KEY_FINGERING_HINT = "fingering_hint"
private const val KEY_NOTE_NAME = "note_name"
private const val KEY_TARGET_SECONDS = "target_seconds"
private const val KEY_HOLD_SECONDS = "hold_seconds"

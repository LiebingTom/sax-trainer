package com.example.saxtrainer.feature.fingering

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
 * Only what the setup screen asks for. How the tool shows notes is already a setting of its own,
 * kept per tool, and is deliberately not copied here.
 *
 * Notes travel as MIDI numbers: the one way of writing a pitch that no display setting, naming
 * convention or transposition can change.
 */
internal fun FingeringUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_NOTE_SET, noteSet)
    putInts(KEY_CUSTOM_NOTES, customNotes.map { it.midiNumber }.sorted())
    put(KEY_ORDER, order)
    put(KEY_SECONDS_PER_NOTE, secondsPerNote)
    put(KEY_CHECK_PLAYING, checkPlaying)
    put(KEY_SUCCESS_DELAY, successDelaySeconds.toDouble())
}

/**
 * The same setup, offered back as the state to open on.
 *
 * [canCheck] is the machine's answer rather than the player's: a device with no microphone cannot
 * check what is played, whatever was chosen last time. [chart] filters remembered notes for the
 * same reason - it says what this build can ask for at all.
 */
internal fun FingeringUiState.withSetup(
    setup: ExerciseSetup,
    chart: FingeringChart,
    canCheck: Boolean,
): FingeringUiState {
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
        secondsPerNote = setup.int(KEY_SECONDS_PER_NOTE, secondsPerNote).coerceAtLeast(1),
        checkPlaying = setup.boolean(KEY_CHECK_PLAYING, checkPlaying) && canCheck,
        successDelaySeconds = setup.double(KEY_SUCCESS_DELAY, successDelaySeconds.toDouble())
            .toFloat()
            .coerceAtLeast(0f),
    )
}

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_ORDER = "order"
private const val KEY_SECONDS_PER_NOTE = "seconds_per_note"
private const val KEY_CHECK_PLAYING = "check_playing"
private const val KEY_SUCCESS_DELAY = "success_delay"

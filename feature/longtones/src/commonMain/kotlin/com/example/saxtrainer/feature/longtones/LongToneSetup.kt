package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.exercise.HeldNote
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.settings.putWrittenNotes
import com.example.saxtrainer.core.settings.writtenNotes

/**
 * How this exercise's setup is written down and read back.
 *
 * The notes are kept the way every note practice keeps them - the set, and the player's own notes
 * as MIDI numbers beside the notes they are written as - so an E flat chosen to be read is not
 * brought back as a D sharp. See [putWrittenNotes].
 */
internal fun LongToneUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_NOTE_SET, noteSet)
    putWrittenNotes(KEY_CUSTOM_NOTES, KEY_CUSTOM_WRITTEN_NOTES, customNotes)
    put(KEY_BEATS_PER_NOTE, selection.beatsPerNote.value)
    put(KEY_SHOW_NEXT_NOTE, showNextNote)
}

/**
 * The same setup, offered back as the state to open on.
 *
 * Anything this build no longer offers is left as it was: a note the chart cannot finger, a hold
 * that is not one of the choices. A setup written when the exercise was a pattern from a starting
 * note names neither a set nor notes, and opens on the default notes.
 */
internal fun LongToneUiState.withSetup(
    setup: ExerciseSetup,
    chart: FingeringChart,
): LongToneUiState {
    if (setup.isEmpty) return this

    val custom = setup.writtenNotes(KEY_CUSTOM_NOTES, KEY_CUSTOM_WRITTEN_NOTES)
        ?.filter { chart.supports(it.note) }
        ?.toSet()
        ?: customNotes
    val noteSet = setup.enum(KEY_NOTE_SET, NoteSet.entries, this.noteSet)
    val notes = NoteSets.notesOf(noteSet, chart, custom)
    val beats = setup.double(KEY_BEATS_PER_NOTE, selection.beatsPerNote.value)
        .coerceAtLeast(0.0)
        .let(::Beats)
        .takeIf { it in beatOptions }
        ?: selection.beatsPerNote

    return copy(
        // Nothing to hold is no exercise: a set that comes out empty keeps the notes already there.
        noteSet = if (notes.isEmpty()) this.noteSet else noteSet,
        customNotes = custom,
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, showNextNote),
        selection = selection.copy(
            writtenNotes = notes.ifEmpty { selection.writtenNotes },
            beatsPerNote = beats,
        ),
    )
}

/**
 * The same setup, offered back as a ready-made exercise.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys, so what is practised cannot depend on which door was used - see the
 * round-trip test.
 *
 * An absent note set is the one the setup screen starts on, so a setup that was never narrowed
 * down - or was written when the exercise was a pattern - is the beginner's notes. Null only where
 * the notes come out empty, which no exercise can hold.
 */
fun longTonePresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): LongTonePreset? {
    val custom = setup.writtenNotes(KEY_CUSTOM_NOTES, KEY_CUSTOM_WRITTEN_NOTES)
        .orEmpty()
        .filter { SaxophoneFingerings.supports(it.note) }
        .toSet()
    val notes = NoteSets.notesOf(
        set = setup.enum(KEY_NOTE_SET, NoteSet.entries, NoteSet.BEGINNER),
        chart = SaxophoneFingerings,
        customNotes = custom,
    )
    if (notes.isEmpty()) return null

    return LongTonePreset(
        notes = notes,
        beatsPerNote = Beats(
            setup.double(KEY_BEATS_PER_NOTE, HeldNote.beatOptionsFrom(4).first().toDouble())
                .coerceAtLeast(1.0),
        ),
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, true),
        autoStart = autoStart,
        editable = editable,
    )
}

// No tempo: it is fixed, so there is nothing to remember. The pattern and starting note a setup
// written by an older build carries are simply not read.
private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_CUSTOM_WRITTEN_NOTES = "custom_written_notes"
private const val KEY_BEATS_PER_NOTE = "beats_per_note"
private const val KEY_SHOW_NEXT_NOTE = "show_next_note"

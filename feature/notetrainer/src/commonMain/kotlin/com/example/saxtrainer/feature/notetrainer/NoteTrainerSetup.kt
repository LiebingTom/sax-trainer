package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.music.Beats

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
    put(KEY_HOLD_BEATS, holdBeats.value)
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
        // A setup remembered before the hold was beats holds seconds counted at 60 BPM, which is
        // the same number of beats - so the old key is read as beats and nothing is lost. A hold
        // this build no longer offers leaves the one already chosen.
        holdBeats = Beats(
            setup.double(KEY_HOLD_BEATS, setup.double(KEY_HOLD_SECONDS, holdBeats.value))
                .coerceAtLeast(0.0),
        ).takeIf { it in holdOptions } ?: holdBeats,
    )
}

/**
 * The same setup, offered back as a ready-made exercise.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys through the same clamps, so what is practised cannot depend on which
 * door was used - see the round-trip test.
 *
 * Null where the setup does not describe an exercise that can be run at all.
 */
fun noteTrainerPresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): NoteTrainerPreset? {
    val custom = setup.ints(KEY_CUSTOM_NOTES)
        .orEmpty()
        .map(Note::fromMidi)
        .filter(SaxophoneFingerings::supports)
        .toSet()
    // An absent note set is the one the setup screen itself starts on, not an empty custom set:
    // a practice nobody has narrowed down is the beginner's notes, and reading it as "no notes"
    // would turn an exercise that has simply never been changed into one that cannot be run.
    val notes = NoteSets.notesOf(
        set = setup.enum(KEY_NOTE_SET, NoteSet.entries, NoteSet.BEGINNER),
        chart = SaxophoneFingerings,
        customNotes = custom,
    )
    if (notes.isEmpty()) return null

    return NoteTrainerPreset(
        notes = notes,
        order = setup.enum(KEY_ORDER, NoteOrder.entries, NoteOrder.RANDOM),
        rounds = setup.int(KEY_ROUNDS, 1).coerceAtLeast(1),
        showFingeringHint = setup.boolean(KEY_FINGERING_HINT, false),
        showNoteName = setup.boolean(KEY_NOTE_NAME, false),
        holdBeats = Beats(
            setup.double(KEY_HOLD_BEATS, setup.double(KEY_HOLD_SECONDS, 0.0)).coerceAtLeast(0.0),
        ),
        autoStart = autoStart,
        editable = editable,
    )
}

// No tempo and no quick time: both are fixed, so there is nothing to remember. A setup written
// when they could be chosen still has them, and they are simply not read.
private const val KEY_HOLD_BEATS = "hold_beats"

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_ORDER = "order"
private const val KEY_ROUNDS = "rounds"
private const val KEY_FINGERING_HINT = "fingering_hint"
private const val KEY_NOTE_NAME = "note_name"
/** What [KEY_HOLD_BEATS] was called when the same number meant seconds. Read, never written. */
private const val KEY_HOLD_SECONDS = "hold_seconds"

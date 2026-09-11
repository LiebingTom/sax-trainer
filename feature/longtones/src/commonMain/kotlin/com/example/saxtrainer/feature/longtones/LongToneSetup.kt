package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.exercise.SequenceCatalog
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How this exercise's setup is written down and read back.
 *
 * The pattern travels as its id and the root as a MIDI number: one is the exercise the player
 * chose rather than the notes it produced, the other is the one way of writing a pitch that no
 * display setting, naming convention or transposition can change.
 */
internal fun LongToneUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_PATTERN, selection.pattern.id)
    put(KEY_ROOT_NOTE, selection.rootNote.midiNumber)
    put(KEY_BEATS_PER_NOTE, selection.beatsPerNote.value)
    put(KEY_SHOW_NEXT_NOTE, showNextNote)
}

/**
 * The same setup, offered back as the state to open on.
 *
 * Anything this build no longer offers is left as it was: a pattern that has gone, a root outside
 * the range the exercise can be built from, a hold that is not one of the choices. What was
 * remembered is a choice among what the app offered, not a licence to widen it.
 */
internal fun LongToneUiState.withSetup(setup: ExerciseSetup): LongToneUiState {
    if (setup.isEmpty) return this

    val pattern = patterns.firstOrNull { it.id == setup.string(KEY_PATTERN) } ?: selection.pattern
    val root = setup.int(KEY_ROOT_NOTE, selection.rootNote.midiNumber)
        .let(Note::fromMidi)
        .takeIf { it in selectableNotes }
        ?: selection.rootNote
    val beats = setup.double(KEY_BEATS_PER_NOTE, selection.beatsPerNote.value)
        .coerceAtLeast(0.0)
        .let(::Beats)
        .takeIf { it in beatOptions }
        ?: selection.beatsPerNote

    return copy(
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, showNextNote),
        selection = selection.copy(
            pattern = pattern,
            rootNote = root,
            beatsPerNote = beats,
        ),
    )
}

/**
 * The same setup, offered back as a ready-made exercise.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys, so what is practised cannot depend on which door was used - see the
 * round-trip test. What this build no longer offers is left to the trainer to fall back from, since
 * only the trainer knows what it offers.
 *
 * Null where the setup names no pattern at all, which is the one thing this cannot make up.
 */
fun longTonePresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): LongTonePreset? {
    val pattern = setup.string(KEY_PATTERN)?.takeIf { it.isNotBlank() } ?: return null
    val root = setup.int(KEY_ROOT_NOTE, NO_ROOT)
    if (root == NO_ROOT) return null

    return LongTonePreset(
        patternId = pattern,
        rootNote = Note.fromMidi(root),
        beatsPerNote = Beats(
            setup.double(KEY_BEATS_PER_NOTE, SequenceCatalog.singleNote.defaultBeatsPerNote.value)
                .coerceAtLeast(1.0),
        ),
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, true),
        autoStart = autoStart,
        editable = editable,
    )
}

/** No pitch is a negative MIDI number, so this cannot be mistaken for a stored root. */
private const val NO_ROOT = -1

// No tempo: it is fixed, so there is nothing to remember. A setup written when it could be chosen
// still has one, and it is simply not read.
private const val KEY_PATTERN = "pattern"
private const val KEY_ROOT_NOTE = "root_note"
private const val KEY_BEATS_PER_NOTE = "beats_per_note"
private const val KEY_SHOW_NEXT_NOTE = "show_next_note"

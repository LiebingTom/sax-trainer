package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Tempo
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
    put(KEY_TEMPO, selection.tempo.bpm)
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
        selection = selection.copy(
            pattern = pattern,
            rootNote = root,
            beatsPerNote = beats,
            tempo = Tempo(setup.int(KEY_TEMPO, selection.tempo.bpm).coerceIn(tempoRange)),
        ),
    )
}

private const val KEY_PATTERN = "pattern"
private const val KEY_ROOT_NOTE = "root_note"
private const val KEY_BEATS_PER_NOTE = "beats_per_note"
private const val KEY_TEMPO = "tempo"

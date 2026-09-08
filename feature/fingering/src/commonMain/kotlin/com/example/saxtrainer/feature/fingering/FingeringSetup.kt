package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

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
    put(KEY_ROUNDS, rounds)
    put(KEY_BEATS_PER_NOTE, beatsPerNote.value)
    put(KEY_TEMPO, tempo.bpm)
    put(KEY_SHOW_NEXT_NOTE, showNextNote)
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
        rounds = setup.int(KEY_ROUNDS, rounds).coerceAtLeast(1),
        // A setup remembered before durations were beats holds seconds counted at 60 BPM, which
        // is the same number of beats - so the old key is read as beats and nothing is lost.
        beatsPerNote = Beats(
            setup.double(
                KEY_BEATS_PER_NOTE,
                setup.int(KEY_SECONDS_PER_NOTE, beatsPerNote.value.toInt()).toDouble(),
            ).coerceAtLeast(1.0),
        ),
        tempo = Tempo(setup.int(KEY_TEMPO, tempo.bpm).coerceIn(tempoRange)),
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, showNextNote),
        checkPlaying = setup.boolean(KEY_CHECK_PLAYING, checkPlaying) && canCheck,
        successDelaySeconds = setup.double(KEY_SUCCESS_DELAY, successDelaySeconds.toDouble())
            .toFloat()
            .coerceAtLeast(0f),
    )
}

/**
 * The same setup, offered back as a ready-made exercise.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys through the same clamps, so what is practised cannot depend on which
 * door was used - see the round-trip test.
 *
 * Null where the setup does not describe an exercise that can be run at all, which is how a stored
 * configuration this build cannot honour is passed over rather than crashing something.
 */
fun fingeringPresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): FingeringPreset? {
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

    return FingeringPreset(
        notes = notes,
        order = setup.enum(KEY_ORDER, NoteOrder.entries, NoteOrder.RANDOM),
        beatsPerNote = Beats(
            setup.double(KEY_BEATS_PER_NOTE, setup.int(KEY_SECONDS_PER_NOTE, DEFAULT_BEATS).toDouble())
                .coerceAtLeast(1.0),
        ),
        tempo = Tempo(setup.int(KEY_TEMPO, DEFAULT_TEMPO)),
        rounds = setup.int(KEY_ROUNDS, 1).coerceAtLeast(1),
        checkPlaying = setup.boolean(KEY_CHECK_PLAYING, false),
        successDelaySeconds = setup.double(KEY_SUCCESS_DELAY, DEFAULT_SUCCESS_DELAY)
            .toFloat()
            .coerceAtLeast(0f),
        showNextNote = setup.boolean(KEY_SHOW_NEXT_NOTE, true),
        autoStart = autoStart,
        editable = editable,
    )
}

/** What the setup screen itself starts at, so a key that was never written reads the same either way. */
private const val DEFAULT_BEATS = 5
private const val DEFAULT_TEMPO = 60
private const val DEFAULT_SUCCESS_DELAY = 1.0

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_ORDER = "order"
private const val KEY_ROUNDS = "rounds"
private const val KEY_BEATS_PER_NOTE = "beats_per_note"

/** What [KEY_BEATS_PER_NOTE] was called when the same number meant seconds. Read, never written. */
private const val KEY_SECONDS_PER_NOTE = "seconds_per_note"
private const val KEY_TEMPO = "tempo"
private const val KEY_SHOW_NEXT_NOTE = "show_next_note"
private const val KEY_CHECK_PLAYING = "check_playing"
private const val KEY_SUCCESS_DELAY = "success_delay"

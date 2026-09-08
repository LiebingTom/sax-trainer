package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
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

/**
 * The same setup, offered back as a ready-made quiz.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys through the same clamps, so what is asked cannot depend on which door was
 * used - see the round-trip test.
 *
 * Null where the setup does not describe a quiz that can be run at all: a quiz needs enough notes to
 * offer a choice between, and fewer than that is a stored configuration this build cannot honour.
 */
fun quizPresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): QuizPreset? {
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
    if (notes.size < QuizQuestionGenerator.MIN_OPTIONS) return null

    return QuizPreset(
        notes = notes,
        mode = setup.enum(KEY_MODE, QuizMode.entries, QuizMode.NOTE_AND_FINGERING),
        questionCount = setup.int(KEY_QUESTION_COUNT, DEFAULT_QUESTION_COUNT).coerceAtLeast(1),
        successDelaySeconds = setup.double(KEY_SUCCESS_DELAY, DEFAULT_SUCCESS_DELAY)
            .coerceAtLeast(0.0),
        autoStart = autoStart,
        editable = editable,
    )
}

/** What the setup screen itself starts at, so a key that was never written reads the same either way. */
private const val DEFAULT_QUESTION_COUNT = 10
private const val DEFAULT_SUCCESS_DELAY = 1.0

private const val KEY_NOTE_SET = "note_set"
private const val KEY_CUSTOM_NOTES = "custom_notes"
private const val KEY_MODE = "mode"
private const val KEY_QUESTION_COUNT = "question_count"
private const val KEY_SUCCESS_DELAY = "success_delay"

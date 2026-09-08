package com.example.saxtrainer.feature.sequence

import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How this trainer's setup is written down and read back.
 *
 * The sequence itself travels as its id rather than as its notes: a definition may gain a note or
 * change its shape between versions, and what the player chose was the exercise, not the material
 * it happened to produce.
 */
internal fun SequenceUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_SEQUENCE, selectedId)
    put(KEY_ADD_INVERSE, addInverse)
    put(KEY_REPEATS, repeats)
    put(KEY_TEMPO, tempo.bpm)
    put(KEY_METRONOME, metronome)
    put(KEY_CHECK_PITCH, checkPitch)
    put(KEY_CHECK_TIMING, checkTiming)
}

/**
 * The same setup, offered back as the state to open on. The material is not rebuilt here; the
 * caller does that, because building it is the ViewModel's own business.
 *
 * What the machine can do outranks what was chosen: a sequence this build no longer has is left
 * alone, a check needs a microphone ([canCheck]), and a click needs somewhere to sound. Timing is
 * measured against the beat, so a remembered timing check brings the click back with it, exactly as
 * switching it on does.
 */
internal fun SequenceUiState.withSetup(setup: ExerciseSetup, canCheck: Boolean): SequenceUiState {
    if (setup.isEmpty) return this

    val remembered = setup.string(KEY_SEQUENCE)?.takeIf { id -> library.any { it.id == id } }
    val definition = library.firstOrNull { it.id == remembered } ?: selected
    val checkTiming = setup.boolean(KEY_CHECK_TIMING, checkTiming)
    val metronome = setup.boolean(KEY_METRONOME, metronome) || checkTiming

    return copy(
        selectedId = definition?.id ?: selectedId,
        addInverse = setup.boolean(KEY_ADD_INVERSE, addInverse),
        repeats = setup.int(KEY_REPEATS, repeats).coerceIn(repeatRange),
        tempo = Tempo(setup.int(KEY_TEMPO, tempo.bpm).coerceIn(tempoRange)),
        metronome = metronome && metronomeAvailable,
        checkPitch = setup.boolean(KEY_CHECK_PITCH, checkPitch) && canCheck,
        checkTiming = checkTiming,
    )
}

/**
 * The same setup, offered back as a ready-made exercise.
 *
 * The other way out of a stored setup: [withSetup] opens the setup screen on it, this starts it.
 * Both read the same keys through the same clamps - including the one that brings the click back
 * with a remembered timing check - so what is practised cannot depend on which door was used.
 *
 * Null where the setup names no sequence at all, which is the one thing this cannot make up.
 */
fun sequencePresetOf(
    setup: ExerciseSetup,
    autoStart: Boolean = true,
    editable: Boolean = false,
): SequencePreset? {
    val sequenceId = setup.string(KEY_SEQUENCE)?.takeIf { it.isNotBlank() } ?: return null
    val checkTiming = setup.boolean(KEY_CHECK_TIMING, false)

    return SequencePreset(
        sequenceId = sequenceId,
        tempo = Tempo(setup.int(KEY_TEMPO, DEFAULT_TEMPO)),
        addInverse = setup.boolean(KEY_ADD_INVERSE, false),
        repeats = setup.int(KEY_REPEATS, 1).coerceAtLeast(1),
        metronome = setup.boolean(KEY_METRONOME, false) || checkTiming,
        checkPitch = setup.boolean(KEY_CHECK_PITCH, false),
        checkTiming = checkTiming,
        autoStart = autoStart,
        editable = editable,
    )
}

/** What the setup screen itself starts at, so a key that was never written reads the same either way. */
private const val DEFAULT_TEMPO = 80

private const val KEY_SEQUENCE = "sequence"
private const val KEY_ADD_INVERSE = "add_inverse"
private const val KEY_REPEATS = "repeats"
private const val KEY_TEMPO = "tempo"
private const val KEY_METRONOME = "metronome"
private const val KEY_CHECK_PITCH = "check_pitch"
private const val KEY_CHECK_TIMING = "check_timing"

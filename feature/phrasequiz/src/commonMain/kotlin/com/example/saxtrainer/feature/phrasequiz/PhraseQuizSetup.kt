package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.phrase.PatternCatalog
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseCatalog
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How this quiz's setup is written down and read back.
 *
 * Only what the setup screen asks for. A library phrase travels as its id, never as its notes - the
 * same convention [PhraseSource.Library] itself follows. A scale pattern travels as the id of one of
 * [ScaleChoice.defaults], the octave chosen on top of it, and the id of the stored pattern from
 * [PatternLibrary] - the recipe, not the notes it produces.
 */
internal fun PhraseQuizUiState.toSetup(): ExerciseSetup = exerciseSetup {
    put(KEY_SOURCE_TYPE, sourceType)
    libraryPhraseId?.let { put(KEY_PHRASE_ID, it) }
    put(KEY_SCALE_CHOICE, scaleChoiceId)
    putInts(KEY_OCTAVES, octaves)
    patternId?.let { put(KEY_PATTERN_ID, it) }
    put(KEY_SHOW_SECONDS, showSeconds)
    put(KEY_REPEATS, repeats)
    put(KEY_MIXED, mixed)
}

/**
 * The same setup, offered back as the state to open on.
 *
 * [phrases] and [patterns] filter a remembered id rather than being trusted to still have it: a
 * phrase or a pattern may have been dropped between versions, and the setup only says what was
 * chosen last time.
 */
internal fun PhraseQuizUiState.withSetup(
    setup: ExerciseSetup,
    phrases: PhraseCatalog,
    patterns: PatternCatalog,
): PhraseQuizUiState {
    if (setup.isEmpty) return this

    val phraseId = setup.string(KEY_PHRASE_ID)?.takeIf { phrases.byId(it) != null } ?: libraryPhraseId
    val scaleChoiceId = setup.string(KEY_SCALE_CHOICE)
        ?.takeIf { id -> scaleChoices.any { it.id == id } }
        ?: scaleChoiceId
    val patternId = setup.string(KEY_PATTERN_ID)?.takeIf { patterns.byId(it) != null } ?: this.patternId

    val storedOctaves = setup.ints(KEY_OCTAVES)
        ?.filter { it in PhraseQuizUiState.OCTAVE_RANGE }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: octaves

    return copy(
        sourceType = setup.enum(KEY_SOURCE_TYPE, PhraseSourceType.entries, sourceType),
        libraryPhraseId = phraseId,
        scaleChoiceId = scaleChoiceId,
        octaves = storedOctaves,
        patternId = patternId,
        showSeconds = setup.double(KEY_SHOW_SECONDS, showSeconds).coerceAtLeast(0.0),
        repeats = setup.int(KEY_REPEATS, repeats).coerceIn(repeatRange),
        mixed = setup.boolean(KEY_MIXED, mixed),
    )
}

/**
 * Overlays a ready-made quiz. Only the run is configured: nothing is written back to the player's
 * settings.
 *
 * The setup screen is skipped rather than pre-filled, unless the preset asks to be edited - and
 * then the resolved phrase is dropped so the screen falls back to whatever library id, or scale
 * choice, octave and pattern id, best matches the preset's source, exactly as it would for a
 * remembered setup.
 */
internal fun PhraseQuizUiState.withPreset(
    preset: PhraseQuizPreset,
    phrases: PhraseCatalog,
    patterns: PatternCatalog,
): PhraseQuizUiState {
    val resolved = preset.source.resolve(phrases, patterns)
    val scalePattern = preset.source as? PhraseSource.ScalePattern
    val matchedScale = scalePattern?.let { p -> scaleChoices.firstOrNull { it.scale == p.scale && it.root == p.root } }

    return copy(
        sourceType = when (preset.source) {
            is PhraseSource.Library -> PhraseSourceType.LIBRARY
            is PhraseSource.ScalePattern -> PhraseSourceType.SCALE_PATTERN
        },
        libraryPhraseId = (preset.source as? PhraseSource.Library)?.phraseId ?: libraryPhraseId,
        scaleChoiceId = matchedScale?.id ?: scaleChoiceId,
        octaves = scalePattern?.octaves ?: octaves,
        patternId = scalePattern?.patternId ?: patternId,
        showSeconds = preset.showSeconds,
        repeats = preset.repeats.coerceIn(repeatRange),
        mixed = preset.mixed,
        presetPhrase = if (preset.editable) null else resolved,
        isPreconfigured = !preset.editable,
    )
}

/**
 * The same setup, offered back as a ready-made quiz.
 *
 * Null where the setup does not describe a quiz that can be run at all: a library id, or a pattern
 * id, this build no longer has, with nothing left to fall back to.
 */
fun phraseQuizPresetOf(
    setup: ExerciseSetup,
    phrases: PhraseCatalog = PhraseLibrary,
    patterns: PatternCatalog = PatternLibrary,
    autoStart: Boolean = true,
    editable: Boolean = false,
): PhraseQuizPreset? {
    val sourceType = setup.enum(KEY_SOURCE_TYPE, PhraseSourceType.entries, PhraseSourceType.LIBRARY)

    val source: PhraseSource = when (sourceType) {
        PhraseSourceType.LIBRARY -> {
            val id = setup.string(KEY_PHRASE_ID) ?: phrases.all.firstOrNull()?.id ?: return null
            if (phrases.byId(id) == null) return null
            PhraseSource.Library(id)
        }

        PhraseSourceType.SCALE_PATTERN -> {
            val choice = setup.string(KEY_SCALE_CHOICE)?.let(ScaleChoice::byId)
                ?: ScaleChoice.defaults.firstOrNull()
                ?: return null
            val octaves = setup.ints(KEY_OCTAVES)
                ?.filter { it in PhraseQuizUiState.OCTAVE_RANGE }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(PhraseQuizUiState.DEFAULT_OCTAVE)
            val patternId = setup.string(KEY_PATTERN_ID) ?: patterns.all.firstOrNull()?.id ?: return null
            if (patterns.byId(patternId) == null) return null
            PhraseSource.ScalePattern(patternId = patternId, scale = choice.scale, root = choice.root, octaves = octaves)
        }
    }

    return PhraseQuizPreset(
        source = source,
        showSeconds = setup.double(KEY_SHOW_SECONDS, DEFAULT_SHOW_SECONDS).coerceAtLeast(0.0),
        repeats = setup.int(KEY_REPEATS, DEFAULT_REPEATS).coerceAtLeast(1),
        mixed = setup.boolean(KEY_MIXED, false),
        autoStart = autoStart,
        editable = editable,
    )
}

private const val DEFAULT_SHOW_SECONDS = 2.5
private const val DEFAULT_REPEATS = 1

private const val KEY_SOURCE_TYPE = "source_type"
private const val KEY_PHRASE_ID = "phrase_id"
private const val KEY_SCALE_CHOICE = "scale_choice"
private const val KEY_OCTAVES = "octaves"
private const val KEY_PATTERN_ID = "pattern_id"
private const val KEY_SHOW_SECONDS = "show_seconds"
private const val KEY_REPEATS = "repeats"
private const val KEY_MIXED = "mixed"

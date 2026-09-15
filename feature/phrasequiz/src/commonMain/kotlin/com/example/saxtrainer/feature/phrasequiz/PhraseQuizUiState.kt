package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.PracticeUnit
import com.example.saxtrainer.core.phrase.ScaleChoice

/** Where the quiz is. */
sealed interface PhraseQuizPhase {
    data object Idle : PhraseQuizPhase

    /** One practice unit's correct notes, shown before they are taken away. */
    data class Showing(val unitIndex: Int, val correct: List<ToneStep>) : PhraseQuizPhase

    /**
     * The unit's notes hidden, [options] shuffled and offered to be tapped back into order.
     *
     * [chosen] holds the options already tapped, in the order they were tapped - indices into
     * [options], never into [correct] - so a tile is only ever "used" or "not yet used", and never
     * itself right or wrong: whether the *order* it went in was right is [Answered.correctOrder].
     */
    data class Asking(
        val unitIndex: Int,
        val correct: List<ToneStep>,
        val options: List<ToneStep>,
        val chosen: List<Int> = emptyList(),
    ) : PhraseQuizPhase {
        /** True once every note has a place, right or wrong. */
        val isSettled: Boolean get() = chosen.size == correct.size

        /** The order built so far, read as pitches - what the top row draws as it fills up. */
        val builtNotes: List<Note?> get() = chosen.map { options[it].note }
    }

    /** Every note placed, held up for a moment before the next unit. */
    data class Answered(
        val unitIndex: Int,
        val correct: List<ToneStep>,
        val options: List<ToneStep>,
        val chosen: List<Int>,
    ) : PhraseQuizPhase {
        val builtNotes: List<Note?> get() = chosen.map { options[it].note }

        /** Right only if every note landed in the right place - which tile it was never matters. */
        val correctOrder: Boolean get() = builtNotes == correct.map { it.note }
    }

    data class Finished(val result: PhraseQuizResult) : PhraseQuizPhase
}

/** How a run went: one unit either had its order rebuilt correctly, or it did not. */
data class PhraseQuizResult(val attempted: List<Boolean>, val plannedCount: Int) {
    val completed: Boolean get() = attempted.size == plannedCount
    val correctCount: Int get() = attempted.count { it }
}

/**
 * Everything the phrase quiz screen renders.
 *
 * [phrase] is derived rather than stored: a library phrase is looked up by id, a scale pattern is a
 * stored [PhraseRecipe] resolved against whichever scale, root and octave were picked. Either way
 * how much is shown at once - [Phrase.trainingBars] - is the content's own to say, never a choice
 * offered here: there is deliberately no grouping picker. A preset overrides all of that with
 * [presetPhrase] instead.
 */
data class PhraseQuizUiState(
    val phase: PhraseQuizPhase = PhraseQuizPhase.Idle,
    val sourceType: PhraseSourceType = PhraseSourceType.LIBRARY,
    val library: List<Phrase> = emptyList(),
    val libraryPhraseId: String? = null,
    /** Whether the phrase-library window is open, listing [library] for the player to choose from. */
    val libraryPickerOpen: Boolean = false,
    val scaleChoices: List<ScaleChoice> = ScaleChoice.defaults,
    val scaleChoiceId: String = ScaleChoice.defaults.first().id,
    /**
     * The octaves a scale pattern is generated in - a setting of its own, apart from which key it
     * is. More than one means a training unit can come from any of them, not that they are woven
     * together - see [PhraseRecipe.resolve].
     */
    val octaves: Set<Int> = setOf(DEFAULT_OCTAVE),
    val patterns: List<PhraseRecipe> = emptyList(),
    val patternId: String? = null,
    /** Whether the scale-pattern window is open: octave, scale and the stored patterns to choose. */
    val patternPickerOpen: Boolean = false,
    val showSeconds: Double = 2.5,
    /** Set only by a preset that is not being edited: the phrase to run, bypassing setup entirely. */
    val presetPhrase: Phrase? = null,
    /** True when a lesson handed the quiz a ready-made run, so there is no setup to show. */
    val isPreconfigured: Boolean = false,
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
) {
    val isRunning: Boolean
        get() = phase is PhraseQuizPhase.Showing || phase is PhraseQuizPhase.Asking ||
            phase is PhraseQuizPhase.Answered

    val scaleChoice: ScaleChoice? get() = scaleChoices.firstOrNull { it.id == scaleChoiceId }
    val pattern: PhraseRecipe? get() = patterns.firstOrNull { it.id == patternId }

    /** The phrase this setup currently describes, however it was chosen. */
    val phrase: Phrase?
        get() = presetPhrase ?: when (sourceType) {
            PhraseSourceType.LIBRARY -> libraryPhraseId?.let { id -> library.firstOrNull { it.id == id } }
            PhraseSourceType.SCALE_PATTERN -> {
                val choice = scaleChoice
                val recipe = pattern
                if (choice != null && recipe != null && octaves.isNotEmpty()) {
                    recipe.resolve(choice.scale, choice.root, octaves)
                } else {
                    null
                }
            }
        }

    /** The practice units the current phrase is walked in - a unit is one or more bars wide. */
    val units: List<PracticeUnit> get() = phrase?.units.orEmpty()

    /** A phrase needs notes to rebuild, so nothing chosen yet is not a quiz that can be started. */
    val canStart: Boolean get() = phrase != null

    companion object {
        /** Octaves offered on the setup screen. */
        val OCTAVE_RANGE: IntRange = 3..6
        val DEFAULT_OCTAVE: Int = 4
    }
}

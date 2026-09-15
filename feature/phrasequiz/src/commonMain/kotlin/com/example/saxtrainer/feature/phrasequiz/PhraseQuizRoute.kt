package com.example.saxtrainer.feature.phrasequiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.phrase.PatternCatalog
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseCatalog
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the phrase quiz.
 *
 * No microphone and no permission: like the note quiz, this is a practice for a pair of eyes and a
 * memory, not an instrument.
 *
 * A [preset] starts the quiz on a ready-made run instead of the setup screen. Where it came from is
 * not the quiz's concern; [onExerciseFinished] simply reports how it went to whoever asked.
 */
@Composable
fun PhraseQuizRoute(
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    phraseCatalog: PhraseCatalog = PhraseLibrary,
    patternCatalog: PatternCatalog = PatternLibrary,
    preset: PhraseQuizPreset? = null,
    onExerciseFinished: (PhraseQuizResult) -> Unit = {},
    /**
     * Where an exercise the player wants to keep is saved, or null where saving makes no sense -
     * a trainer running somebody else's lesson is not making one up.
     */
    onAddToLessons: (() -> Unit)? = null,
    /** Whether the player has starred this practice. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense - inside a lesson, again. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: PhraseQuizViewModel = viewModel {
        PhraseQuizViewModel(
            settingsRepository = settingsRepository,
            exerciseSetups = exerciseSetups,
            phraseCatalog = phraseCatalog,
            patternCatalog = patternCatalog,
            preset = preset,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The setup is what is being saved, so it is written down on the way rather than being hoped
    // for: a practice nobody has touched is still a practice.
    val addToLessons: (() -> Unit)? = onAddToLessons?.let { open ->
        {
            viewModel.rememberSetupNow()
            open()
        }
    }

    val startNow by rememberUpdatedState(viewModel::start)
    LaunchedEffect(viewModel) {
        if (viewModel.consumeAutoStart()) startNow()
    }

    // Reported once per finished run: Finished carries the result, so the same value never fires
    // twice.
    val report by rememberUpdatedState(onExerciseFinished)
    LaunchedEffect(state.phase) {
        (state.phase as? PhraseQuizPhase.Finished)?.let { report(it.result) }
    }

    // A quiz the player cannot see should not be counting down to its next unit.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    PhraseQuizScreen(
        state = state,
        onOpenLibraryPicker = viewModel::openLibraryPicker,
        onCloseLibraryPicker = viewModel::closeLibraryPicker,
        onSelectLibraryPhrase = viewModel::selectLibraryPhrase,
        onOpenPatternPicker = viewModel::openPatternPicker,
        onClosePatternPicker = viewModel::closePatternPicker,
        onSelectScaleChoice = viewModel::selectScaleChoice,
        onToggleOctave = viewModel::toggleOctave,
        onSelectPattern = viewModel::selectPattern,
        onChoose = viewModel::choose,
        onSkipRevealDelay = viewModel::skipRevealDelay,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        onAddToLessons = addToLessons,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

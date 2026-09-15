package com.example.saxtrainer.feature.phrasetrainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.rememberMicrophoneAccess
import com.example.saxtrainer.core.phrase.PatternCatalog
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseCatalog
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the phrase trainer.
 *
 * The microphone is what the exercise is made of - a unit is finished by being played - so it is
 * asked for at Start, and a refusal leaves a screen that says why rather than a silent exercise
 * that could never end.
 *
 * A [preset] starts the trainer on a ready-made run instead of the setup screen. Where that run
 * came from is not the trainer's concern; [onExerciseFinished] simply reports how it went.
 */
@Composable
fun PhraseTrainerRoute(
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    audioCapture: AudioCapture,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    phraseCatalog: PhraseCatalog = PhraseLibrary,
    patternCatalog: PatternCatalog = PatternLibrary,
    preset: PhraseTrainerPreset? = null,
    onExerciseFinished: (PhraseTrainerResult) -> Unit = {},
    /**
     * Where an exercise the player wants to keep is saved, or null where saving makes no sense -
     * a trainer running somebody else's lesson is not making one up.
     */
    onAddToLessons: (() -> Unit)? = null,
    /** Whether the player has starred this practice. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense - inside a lesson, again. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: PhraseTrainerViewModel = viewModel {
        PhraseTrainerViewModel(
            settingsRepository = settingsRepository,
            exerciseSetups = exerciseSetups,
            audioCapture = audioCapture,
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
    val microphone = rememberMicrophoneAccess()

    val start: () -> Unit = {
        microphone.request { granted ->
            if (granted) viewModel.start() else viewModel.onMicrophoneUnavailable()
        }
    }

    // A preset that asked to start goes through the same door as the Start button, so it still
    // gets its permission prompt. The ViewModel answers this once.
    val startNow by rememberUpdatedState(start)
    LaunchedEffect(viewModel) {
        if (viewModel.consumeAutoStart()) startNow()
    }

    // Reported once per finished run: Finished carries the result, so the same value never fires
    // twice.
    val report by rememberUpdatedState(onExerciseFinished)
    LaunchedEffect(state.phase) {
        (state.phase as? PhraseTrainerPhase.Finished)?.let { report(it.result) }
    }

    // An exercise the player cannot see should not hold the microphone open.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    PhraseTrainerScreen(
        state = state,
        onOpenLibraryPicker = viewModel::openLibraryPicker,
        onCloseLibraryPicker = viewModel::closeLibraryPicker,
        onSelectLibraryPhrase = viewModel::selectLibraryPhrase,
        onOpenPatternPicker = viewModel::openPatternPicker,
        onClosePatternPicker = viewModel::closePatternPicker,
        onSelectScaleChoice = viewModel::selectScaleChoice,
        onToggleOctave = viewModel::toggleOctave,
        onSelectPattern = viewModel::selectPattern,
        onCheckDurationChange = viewModel::setCheckDuration,
        onStart = start,
        onTogglePause = viewModel::togglePause,
        onSkip = viewModel::skip,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        onAddToLessons = addToLessons,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

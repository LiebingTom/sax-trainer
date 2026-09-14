package com.example.saxtrainer.feature.notetrainer

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
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the note trainer.
 *
 * The microphone is what the exercise is made of - a note is finished by being heard - so it is
 * asked for at Start, and a refusal leaves a screen that says why rather than a silent exercise
 * that could never end.
 *
 * A [preset] starts the trainer on a ready-made exercise instead of the setup screen. Where that
 * exercise came from is not the trainer's concern; [onExerciseFinished] simply reports how the run
 * turned out to whoever asked for it.
 */
@Composable
fun NoteTrainerRoute(
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    audioCapture: AudioCapture,
    newMetronome: MetronomeFactory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preset: NoteTrainerPreset? = null,
    onExerciseFinished: (NoteTrainerResult) -> Unit = {},
    /**
     * Where an exercise the player wants to keep is saved, or null where saving makes no sense -
     * a trainer running somebody else's lesson is not making one up.
     */
    onAddToLessons: (() -> Unit)? = null,
    /** Whether the player has starred this practice. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense - inside a lesson, again. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: NoteTrainerViewModel = viewModel {
        NoteTrainerViewModel(
            settingsRepository = settingsRepository,
            exerciseSetups = exerciseSetups,
            audioCapture = audioCapture,
            // Only ever heard during the count-in; the exercise itself makes no sound.
            metronome = newMetronome.create(),
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
        (state.phase as? NoteTrainerPhase.Finished)?.let { report(it.result) }
    }

    // An exercise the player cannot see should not hold the microphone open.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    NoteTrainerScreen(
        state = state,
        onSelectNoteSet = viewModel::selectNoteSet,
        onEditCustomNotes = viewModel::editCustomNotes,
        onToggleCustomNote = viewModel::toggleCustomNote,
        onSelectAllCustomNotes = viewModel::selectAllCustomNotes,
        onClearCustomNotes = viewModel::clearCustomNotes,
        onApplyCustomNotes = viewModel::applyCustomNotes,
        onCancelCustomNotes = viewModel::cancelCustomNotes,
        onRandomChange = viewModel::setRandomOrder,
        onRoundsChange = viewModel::setRounds,
        onShowFingeringHintChange = viewModel::setShowFingeringHint,
        onShowNoteNameChange = viewModel::setShowNoteName,
        onHoldChange = viewModel::setHoldBeats,
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

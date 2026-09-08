package com.example.saxtrainer.feature.fingering

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
 * Stateful entry point of the fingering trainer.
 *
 * The exercise runs on its own clock whether or not the player makes a sound; the microphone is only
 * involved when "Play the notes" is switched on, and it is asked for at that point rather than up
 * front.
 *
 * A [preset] starts the trainer on a ready-made exercise instead of the setup screen. Where that
 * exercise came from is not the trainer's concern; [onExerciseFinished] simply reports how the run
 * turned out to whoever asked for it.
 */
@Composable
fun FingeringRoute(
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    audioCapture: AudioCapture,
    newMetronome: MetronomeFactory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preset: FingeringPreset? = null,
    onExerciseFinished: (FingeringResult) -> Unit = {},
    /**
     * Where an exercise the player wants to keep is saved, or null where saving makes no sense -
     * a trainer running somebody else's lesson is not making one up.
     */
    onAddToLessons: (() -> Unit)? = null,
    viewModel: FingeringViewModel = viewModel {
        FingeringViewModel(
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

    // The microphone is only ever asked for when playing is checked; a refusal switches checking
    // off instead of stopping the exercise.
    val start: () -> Unit = {
        if (state.checkPlaying) {
            microphone.request { granted ->
                if (granted) viewModel.start() else viewModel.onMicrophoneUnavailable()
            }
        } else {
            viewModel.start()
        }
    }

    // A preset that asked to start goes through the same door as the Start button, so a listening
    // exercise still gets its permission prompt. The ViewModel answers this once.
    val startNow by rememberUpdatedState(start)
    LaunchedEffect(viewModel) {
        if (viewModel.consumeAutoStart()) startNow()
    }

    // Reported once per finished run: Finished carries the result, so the same value never fires
    // twice.
    val report by rememberUpdatedState(onExerciseFinished)
    LaunchedEffect(state.phase) {
        (state.phase as? FingeringPhase.Finished)?.let { report(it.result) }
    }

    // An exercise the player cannot see should not keep running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    FingeringScreen(
        state = state,
        onSelectNoteSet = viewModel::selectNoteSet,
        onEditCustomNotes = viewModel::editCustomNotes,
        onToggleCustomNote = viewModel::toggleCustomNote,
        onSelectAllCustomNotes = viewModel::selectAllCustomNotes,
        onClearCustomNotes = viewModel::clearCustomNotes,
        onApplyCustomNotes = viewModel::applyCustomNotes,
        onCancelCustomNotes = viewModel::cancelCustomNotes,
        onRandomChange = viewModel::setRandomOrder,
        onBeatsChange = viewModel::setBeatsPerNote,
        onTempoChange = viewModel::setTempo,
        onShowNextNoteChange = viewModel::setShowNextNote,
        onCheckPlayingChange = viewModel::setCheckPlaying,
        onSuccessDelayChange = viewModel::setSuccessDelaySeconds,
        onNoteDisplayChange = viewModel::setNoteDisplay,
        onSkipSuccessDelay = viewModel::skipSuccessDelay,
        onStart = start,
        onTogglePause = viewModel::togglePause,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        onAddToLessons = addToLessons,
        modifier = modifier,
    )
}

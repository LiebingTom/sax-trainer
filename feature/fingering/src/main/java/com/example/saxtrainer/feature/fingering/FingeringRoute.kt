package com.example.saxtrainer.feature.fingering

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioPermission
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the fingering trainer.
 *
 * The exercise runs on its own clock whether or not the player makes a sound; the microphone is only
 * involved when checking is switched on, and it is asked for at that point rather than up front.
 */
@Composable
fun FingeringRoute(
    settingsRepository: SettingsRepository,
    audioCapture: AudioCapture,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FingeringViewModel = viewModel {
        FingeringViewModel(settingsRepository = settingsRepository, audioCapture = audioCapture)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The microphone is only ever asked for when the player turned checking on themselves; a
    // refusal switches checking off instead of stopping the exercise.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.start() else viewModel.onMicrophoneUnavailable()
    }

    // An exercise the player cannot see should not keep running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    FingeringScreen(
        state = state,
        onToggleNote = viewModel::toggleNote,
        onSelectAll = viewModel::selectAllNotes,
        onClearNotes = viewModel::clearNotes,
        onRandomChange = viewModel::setRandomOrder,
        onSecondsChange = viewModel::setSecondsPerNote,
        onCheckPlayingChange = viewModel::setCheckPlaying,
        onCheckModeChange = viewModel::setCheckMode,
        onNoteDisplayChange = viewModel::setNoteDisplay,
        onStart = {
            if (state.checkPlaying && !AudioPermission.isGranted(context)) {
                permissionLauncher.launch(AudioPermission.RECORD_AUDIO)
            } else {
                viewModel.start()
            }
        },
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
    )
}

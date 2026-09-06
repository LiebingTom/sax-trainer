package com.example.saxtrainer.feature.longtones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Stateful entry point of the long tone feature.
 *
 * The microphone permission is requested here, when the user actually starts an exercise, and never
 * on the landing page.
 */
@Composable
fun LongToneRoute(
    audioCapture: AudioCapture,
    newMetronome: MetronomeFactory,
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LongToneViewModel = viewModel {
        // The click counts the exercise in and stops before the microphone opens.
        LongToneViewModel(
            audioCapture = audioCapture,
            settingsRepository = settingsRepository,
            exerciseSetups = exerciseSetups,
            metronome = newMetronome.create(),
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val microphone = rememberMicrophoneAccess()
    var permissionDenied by remember { mutableStateOf(false) }

    // Recording must not continue while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    LongToneScreen(
        state = state,
        onSelectPattern = viewModel::selectPattern,
        onSelectRootNote = viewModel::selectRootNote,
        onSelectBeats = viewModel::selectBeatsPerNote,
        onSetTempo = viewModel::setTempo,
        onSelectNoteDisplay = viewModel::selectNoteDisplay,
        onStart = {
            microphone.request { granted ->
                permissionDenied = !granted
                if (granted) viewModel.start()
            }
        },
        onTogglePause = viewModel::togglePause,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
        permissionDenied = permissionDenied,
        onOpenAppSettings = microphone::openSettings,
    )
}

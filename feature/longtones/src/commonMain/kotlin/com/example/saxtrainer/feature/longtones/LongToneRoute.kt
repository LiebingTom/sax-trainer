package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect

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
    preset: LongTonePreset? = null,
    onExerciseFinished: (LongToneResult) -> Unit = {},
    /**
     * Where an exercise the player wants to keep is saved, or null where saving makes no sense -
     * a trainer running somebody else's lesson is not making one up.
     */
    onAddToLessons: (() -> Unit)? = null,
    /** Whether the player has starred this practice. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense - inside a lesson, again. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: LongToneViewModel = viewModel {
        // The click counts the exercise in and stops before the microphone opens.
        LongToneViewModel(
            audioCapture = audioCapture,
            settingsRepository = settingsRepository,
            exerciseSetups = exerciseSetups,
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
    var permissionDenied by remember { mutableStateOf(false) }

    val start: () -> Unit = {
        microphone.request { granted ->
            permissionDenied = !granted
            if (granted) viewModel.start()
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
        (state.phase as? LongTonePhase.Finished)?.let { report(it.result) }
    }

    // Recording must not continue while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    LongToneScreen(
        state = state,
        onOpenNotePicker = viewModel::openNotePicker,
        onNotePickerChange = viewModel::updateNotePicker,
        onNotePickerStartFrom = viewModel::startNotePickerFrom,
        onApplyNotePicker = viewModel::applyNotePicker,
        onCloseNotePicker = viewModel::closeNotePicker,
        phrases = PhraseLibrary.all,
        patterns = PatternLibrary.all,
        onSelectBeats = viewModel::selectBeatsPerNote,
        onSelectNoteDisplay = viewModel::selectNoteDisplay,
        onShowNextNote = viewModel::setShowNextNote,
        onStart = start,
        onTogglePause = viewModel::togglePause,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        onAddToLessons = addToLessons,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
        permissionDenied = permissionDenied,
        onOpenAppSettings = microphone::openSettings,
    )
}

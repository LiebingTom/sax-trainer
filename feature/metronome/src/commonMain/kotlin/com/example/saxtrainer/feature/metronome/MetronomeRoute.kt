package com.example.saxtrainer.feature.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.audio.metronome.ActiveMetronome
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.designsystem.component.rememberNotificationPermissionAsker
import com.example.saxtrainer.core.settings.ExerciseSetupRepository

/**
 * Stateful entry point of the metronome feature. The app module navigates here and supplies only
 * [newMetronome], [exerciseSetups] and [onBack], so the feature stays independent of both the
 * navigation implementation and of how a click is produced.
 *
 * Starring the metronome is offered on [MetronomeSetupRoute], the screen that leads here, not on
 * this one - a player mid-click has no business reaching for it.
 */
@Composable
fun MetronomeRoute(
    newMetronome: MetronomeFactory,
    exerciseSetups: ExerciseSetupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the open click is shown outside this screen, where the platform has such a place. */
    active: ActiveMetronome? = null,
    /**
     * Whether the click plays on with the app in the background and the phone locked - only where
     * [active] is shown somewhere the player can see and stop it, as Android's notification is.
     */
    playsInBackground: Boolean = false,
    viewModel: MetronomeViewModel = viewModel {
        MetronomeViewModel(engine = newMetronome.create(), exerciseSetups = exerciseSetups, active = active)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val askForNotifications = rememberNotificationPermissionAsker()

    // Without somewhere to show it and stop it, a click never plays on unseen in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!playsInBackground) viewModel.pause()
    }

    MetronomeScreen(
        state = state,
        onToggleRunning = {
            if (playsInBackground && !state.isRunning) askForNotifications()
            viewModel.toggleRunning()
        },
        onBpmChange = viewModel::setBpm,
        onNudgeBpm = viewModel::nudgeBpm,
        onTap = viewModel::tap,
        // Leaving the metronome stops it at once, not when the screen has finished going.
        onBack = {
            viewModel.pause()
            onBack()
        },
        modifier = modifier,
    )
}

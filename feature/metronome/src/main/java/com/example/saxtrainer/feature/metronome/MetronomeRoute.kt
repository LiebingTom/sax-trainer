package com.example.saxtrainer.feature.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory

/**
 * Stateful entry point of the metronome feature. The app module navigates here and supplies only
 * [newMetronome] and [onBack], so the feature stays independent of both the navigation
 * implementation and of how a click is produced.
 */
@Composable
fun MetronomeRoute(
    newMetronome: MetronomeFactory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel { MetronomeViewModel(newMetronome.create()) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Practising should never continue while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.pause()
    }

    MetronomeScreen(
        state = state,
        onToggleRunning = viewModel::toggleRunning,
        onBpmChange = viewModel::setBpm,
        onNudgeBpm = viewModel::nudgeBpm,
        onTap = viewModel::tap,
        onBack = onBack,
        modifier = modifier,
    )
}

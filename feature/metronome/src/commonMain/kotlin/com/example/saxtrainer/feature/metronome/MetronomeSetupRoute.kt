package com.example.saxtrainer.feature.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.settings.ExerciseSetupRepository

/**
 * Stateful entry point of the metronome's settings screen. The app module navigates here and
 * supplies only [exerciseSetups], [onStart] and [onBack], so the feature stays independent of both
 * the navigation implementation and of the metronome practice screen it leads to.
 */
@Composable
fun MetronomeSetupRoute(
    exerciseSetups: ExerciseSetupRepository,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the player has starred the metronome. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: MetronomeSetupViewModel = viewModel {
        MetronomeSetupViewModel(exerciseSetups = exerciseSetups)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MetronomeSetupScreen(
        state = state,
        onSelectTimeSignature = viewModel::selectTimeSignature,
        onSelectAccentFirstBeat = viewModel::setAccentFirstBeat,
        onSelectVisualizeHalfBeats = viewModel::setVisualizeHalfBeats,
        onSelectHalfBeatSound = viewModel::setHalfBeatSound,
        onStart = onStart,
        onBack = onBack,
        modifier = modifier,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
    )
}

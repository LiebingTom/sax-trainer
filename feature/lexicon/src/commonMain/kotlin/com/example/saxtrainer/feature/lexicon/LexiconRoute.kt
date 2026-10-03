package com.example.saxtrainer.feature.lexicon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the fingering lexicon. The app navigates here and supplies only the
 * settings and the way back, so the feature stays independent of the navigation implementation.
 */
@Composable
fun LexiconRoute(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the player has starred the lexicon. */
    isFavorite: Boolean = false,
    /** Stars or unstars it, or null where starring makes no sense. */
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: LexiconViewModel = viewModel { LexiconViewModel(settingsRepository = settingsRepository) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LexiconScreen(
        state = state,
        onSelect = viewModel::select,
        onLower = viewModel::lower,
        onHigher = viewModel::higher,
        onBack = onBack,
        modifier = modifier,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
    )
}

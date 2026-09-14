package com.example.saxtrainer.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.settings.SettingsRepository

/** Stateful entry point of the settings feature. Dependencies are supplied by the app module. */
@Composable
fun SettingsRoute(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onInstrumentSelected = viewModel::selectInstrument,
        onNoteDisplaySelected = viewModel::selectNoteDisplay,
        onNoteNamingSelected = viewModel::selectNoteNaming,
        onCountInSelected = viewModel::selectCountInBeats,
        onCountInClickChanged = viewModel::setCountInClick,
        onThemeModeSelected = viewModel::selectThemeMode,
        onThemeColorSelected = viewModel::selectThemeColor,
        onBack = onBack,
        modifier = modifier,
    )
}

package com.example.saxtrainer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val instruments: List<Instrument> = Instrument.entries,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { SettingsUiState(settings = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
            initialValue = SettingsUiState(settings = settingsRepository.settings.value),
        )

    fun selectInstrument(instrument: Instrument) {
        settingsRepository.setInstrument(instrument)
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}

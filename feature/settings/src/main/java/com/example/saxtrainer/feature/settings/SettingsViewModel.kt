package com.example.saxtrainer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val instruments: List<Instrument> = Instrument.entries,
    val noteDisplays: List<NoteDisplay> = NoteDisplay.entries,
    val noteNamings: List<NoteNaming> = NoteNaming.entries,
    val countInChoices: List<Int> = AppSettings.COUNT_IN_CHOICES,
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

    fun selectNoteDisplay(noteDisplay: NoteDisplay) {
        settingsRepository.setNoteDisplay(noteDisplay)
    }

    fun selectNoteNaming(noteNaming: NoteNaming) {
        settingsRepository.setNoteNaming(noteNaming)
    }

    fun selectCountInBeats(beats: Int) {
        settingsRepository.setCountInBeats(beats)
    }

    fun setCountInClick(enabled: Boolean) {
        settingsRepository.setCountInClick(enabled)
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}

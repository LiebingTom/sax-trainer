package com.example.saxtrainer.core.testing

import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings held in memory, with none of SharedPreferences' storage.
 *
 * [initial] defaults to no count-in, because most tests are about what the exercise does and would
 * otherwise spend four beats getting there. A test about the count-in passes its own.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(countInBeats = 0),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state

    override fun setInstrument(instrument: Instrument) {
        state.value = state.value.copy(instrument = instrument)
    }

    override fun setNoteNaming(noteNaming: NoteNaming) {
        state.value = state.value.copy(noteNaming = noteNaming)
    }

    override fun setNoteDisplay(noteDisplay: NoteDisplay) {
        state.value = state.value.copy(noteDisplay = noteDisplay)
    }

    override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
        state.value = state.value.copy(
            noteDisplayOverrides = state.value.noteDisplayOverrides + (tool to noteDisplay),
        )
    }

    override fun setCountInBeats(beats: Int) {
        state.value = state.value.copy(countInBeats = beats)
    }

    override fun setCountInClick(enabled: Boolean) {
        state.value = state.value.copy(countInClick = enabled)
    }
}

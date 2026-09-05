package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.Storage
import org.w3c.dom.get

/**
 * [SettingsRepository] backed by the browser's `localStorage`.
 *
 * The web counterpart of [SharedPreferencesSettingsRepository][SettingsRepository]: same names,
 * same fallbacks, same "current values in memory, mirrored to storage" shape. Only the store
 * differs, and `localStorage` is already scoped to this origin, so there is no file to name.
 *
 * Everything `localStorage` holds is a string, so the two non-string settings are written as one
 * and read back leniently - a value that is not a number, or not a boolean, reads as absent and so
 * as the default, exactly like a name this version does not recognise.
 *
 * [storage] is a parameter only so a test can hand in a store of its own; the app uses the default.
 */
class LocalStorageSettingsRepository(
    private val storage: Storage = localStorage,
) : SettingsRepository {

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        _settings.value = AppSettings(
            instrument = StoredSettings.instrumentOf(storage[StoredSettings.KEY_INSTRUMENT]),
            noteNaming = StoredSettings.noteNamingOf(storage[StoredSettings.KEY_NOTE_NAMING]),
            noteDisplay = StoredSettings.noteDisplayOf(storage[StoredSettings.KEY_NOTE_DISPLAY]),
            noteDisplayOverrides = readNoteDisplayOverrides(),
            countInBeats = StoredSettings.countInBeatsOf(
                storage[StoredSettings.KEY_COUNT_IN_BEATS]?.toIntOrNull(),
            ),
            countInClick = StoredSettings.countInClickOf(
                storage[StoredSettings.KEY_COUNT_IN_CLICK]?.toBooleanStrictOrNull(),
            ),
        )
    }

    override fun setInstrument(instrument: Instrument) {
        if (_settings.value.instrument == instrument) return
        _settings.value = _settings.value.copy(instrument = instrument)
        storage.setItem(StoredSettings.KEY_INSTRUMENT, instrument.name)
    }

    override fun setNoteNaming(noteNaming: NoteNaming) {
        if (_settings.value.noteNaming == noteNaming) return
        _settings.value = _settings.value.copy(noteNaming = noteNaming)
        storage.setItem(StoredSettings.KEY_NOTE_NAMING, noteNaming.name)
    }

    override fun setNoteDisplay(noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplay == noteDisplay) return
        _settings.value = _settings.value.copy(noteDisplay = noteDisplay)
        storage.setItem(StoredSettings.KEY_NOTE_DISPLAY, noteDisplay.name)
    }

    override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplayOverrides[tool] == noteDisplay) return
        _settings.value = _settings.value.copy(
            noteDisplayOverrides = _settings.value.noteDisplayOverrides + (tool to noteDisplay),
        )
        storage.setItem(StoredSettings.overrideKey(tool), noteDisplay.name)
    }

    override fun setCountInBeats(beats: Int) {
        val clamped = beats.coerceAtLeast(0)
        if (_settings.value.countInBeats == clamped) return
        _settings.value = _settings.value.copy(countInBeats = clamped)
        storage.setItem(StoredSettings.KEY_COUNT_IN_BEATS, clamped.toString())
    }

    override fun setCountInClick(enabled: Boolean) {
        if (_settings.value.countInClick == enabled) return
        _settings.value = _settings.value.copy(countInClick = enabled)
        storage.setItem(StoredSettings.KEY_COUNT_IN_CLICK, enabled.toString())
    }

    private fun readNoteDisplayOverrides(): Map<TrainingTool, NoteDisplay> =
        TrainingTool.entries.mapNotNull { tool ->
            StoredSettings.noteDisplayOverrideOf(storage[StoredSettings.overrideKey(tool)])
                ?.let { tool to it }
        }.toMap()
}

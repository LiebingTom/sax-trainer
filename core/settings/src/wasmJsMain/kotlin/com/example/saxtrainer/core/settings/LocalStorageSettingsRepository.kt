package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
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
            octaveNumbering = StoredSettings.octaveNumberingOf(storage[StoredSettings.KEY_OCTAVE_NUMBERING]),
            noteDisplay = StoredSettings.noteDisplayOf(storage[StoredSettings.KEY_NOTE_DISPLAY]),
            noteDisplayOverrides = readNoteDisplayOverrides(),
            countInBeats = StoredSettings.countInBeatsOf(
                storage[StoredSettings.KEY_COUNT_IN_BEATS]?.toIntOrNull(),
            ),
            countInClick = StoredSettings.countInClickOf(
                storage[StoredSettings.KEY_COUNT_IN_CLICK]?.toBooleanStrictOrNull(),
            ),
            favorites = StoredSettings.favoritesOf(storage[StoredSettings.KEY_FAVORITES]),
            themeMode = StoredSettings.themeModeOf(storage[StoredSettings.KEY_THEME_MODE]),
            themeColor = StoredSettings.themeColorOf(storage[StoredSettings.KEY_THEME_COLOR]),
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

    override fun setOctaveNumbering(octaveNumbering: OctaveNumbering) {
        if (_settings.value.octaveNumbering == octaveNumbering) return
        _settings.value = _settings.value.copy(octaveNumbering = octaveNumbering)
        storage.setItem(StoredSettings.KEY_OCTAVE_NUMBERING, octaveNumbering.name)
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

    override fun setFavorite(id: String, favorite: Boolean) {
        val was = _settings.value.favorites
        val now = if (favorite) was + id else was - id
        if (was == now) return
        _settings.value = _settings.value.copy(favorites = now)
        storage.setItem(
            StoredSettings.KEY_FAVORITES,
            StoredSettings.encodeFavorites(now),
        )
    }

    override fun setThemeMode(themeMode: ThemeMode) {
        if (_settings.value.themeMode == themeMode) return
        _settings.value = _settings.value.copy(themeMode = themeMode)
        storage.setItem(StoredSettings.KEY_THEME_MODE, themeMode.name)
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        if (_settings.value.themeColor == themeColor) return
        _settings.value = _settings.value.copy(themeColor = themeColor)
        storage.setItem(StoredSettings.KEY_THEME_COLOR, themeColor.name)
    }

    private fun readNoteDisplayOverrides(): Map<TrainingTool, NoteDisplay> =
        TrainingTool.entries.mapNotNull { tool ->
            StoredSettings.noteDisplayOverrideOf(storage[StoredSettings.overrideKey(tool)])
                ?.let { tool to it }
        }.toMap()
}

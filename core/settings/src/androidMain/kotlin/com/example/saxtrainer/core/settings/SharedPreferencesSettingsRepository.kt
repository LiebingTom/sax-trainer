package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.core.content.edit
import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SettingsRepository] backed by [android.content.SharedPreferences].
 *
 * Current values live in memory and are mirrored to disk asynchronously by
 * [android.content.SharedPreferences.Editor.apply], which keeps reads free and avoids pulling in a
 * persistence library for a handful of values.
 *
 * Which name a value is stored under, and what an unreadable one falls back to, is [StoredSettings]
 * and shared with every other platform.
 */
class SharedPreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {

    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(StoredSettings.NAME, Context.MODE_PRIVATE)
    }

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        _settings.value = AppSettings(
            instrument = StoredSettings.instrumentOf(read(StoredSettings.KEY_INSTRUMENT)),
            noteNaming = StoredSettings.noteNamingOf(read(StoredSettings.KEY_NOTE_NAMING)),
            noteDisplay = StoredSettings.noteDisplayOf(read(StoredSettings.KEY_NOTE_DISPLAY)),
            noteDisplayOverrides = readNoteDisplayOverrides(),
            countInBeats = StoredSettings.countInBeatsOf(readCountInBeats()),
            countInClick = StoredSettings.countInClickOf(readCountInClick()),
        )
    }

    override fun setInstrument(instrument: Instrument) {
        if (_settings.value.instrument == instrument) return
        _settings.value = _settings.value.copy(instrument = instrument)
        preferences.edit { putString(StoredSettings.KEY_INSTRUMENT, instrument.name) }
    }

    override fun setNoteNaming(noteNaming: NoteNaming) {
        if (_settings.value.noteNaming == noteNaming) return
        _settings.value = _settings.value.copy(noteNaming = noteNaming)
        preferences.edit { putString(StoredSettings.KEY_NOTE_NAMING, noteNaming.name) }
    }

    override fun setNoteDisplay(noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplay == noteDisplay) return
        _settings.value = _settings.value.copy(noteDisplay = noteDisplay)
        preferences.edit { putString(StoredSettings.KEY_NOTE_DISPLAY, noteDisplay.name) }
    }

    override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplayOverrides[tool] == noteDisplay) return
        _settings.value = _settings.value.copy(
            noteDisplayOverrides = _settings.value.noteDisplayOverrides + (tool to noteDisplay),
        )
        preferences.edit { putString(StoredSettings.overrideKey(tool), noteDisplay.name) }
    }

    override fun setCountInBeats(beats: Int) {
        val clamped = beats.coerceAtLeast(0)
        if (_settings.value.countInBeats == clamped) return
        _settings.value = _settings.value.copy(countInBeats = clamped)
        preferences.edit { putInt(StoredSettings.KEY_COUNT_IN_BEATS, clamped) }
    }

    override fun setCountInClick(enabled: Boolean) {
        if (_settings.value.countInClick == enabled) return
        _settings.value = _settings.value.copy(countInClick = enabled)
        preferences.edit { putBoolean(StoredSettings.KEY_COUNT_IN_CLICK, enabled) }
    }

    private fun read(key: String): String? = preferences.getString(key, null)

    // Null rather than a default, so that a stored zero is told apart from nothing stored: zero is
    // a count-in the settings screen offers, and it is also what a missing Int preference reads as.
    private fun readCountInBeats(): Int? =
        if (preferences.contains(StoredSettings.KEY_COUNT_IN_BEATS)) {
            preferences.getInt(StoredSettings.KEY_COUNT_IN_BEATS, 0)
        } else {
            null
        }

    private fun readCountInClick(): Boolean? =
        if (preferences.contains(StoredSettings.KEY_COUNT_IN_CLICK)) {
            preferences.getBoolean(StoredSettings.KEY_COUNT_IN_CLICK, false)
        } else {
            null
        }

    private fun readNoteDisplayOverrides(): Map<TrainingTool, NoteDisplay> =
        TrainingTool.entries.mapNotNull { tool ->
            StoredSettings.noteDisplayOverrideOf(read(StoredSettings.overrideKey(tool)))
                ?.let { tool to it }
        }.toMap()
}

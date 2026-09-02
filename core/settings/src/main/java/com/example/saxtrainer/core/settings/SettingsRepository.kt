package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for [AppSettings]. Features observe it; none of them owns its own copy of
 * a setting.
 */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun setInstrument(instrument: Instrument)

    /** Sets the default every tool follows unless it has been given its own preference. */
    fun setNoteDisplay(noteDisplay: NoteDisplay)

    /** Gives one tool a preference of its own. */
    fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay)
}

/**
 * [SettingsRepository] backed by [android.content.SharedPreferences].
 *
 * Current values live in memory and are mirrored to disk asynchronously by
 * [android.content.SharedPreferences.Editor.apply], which keeps reads free and avoids pulling in a
 * persistence library for a handful of values.
 */
class SharedPreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {

    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        _settings.value = AppSettings(
            instrument = readInstrument(),
            noteDisplay = readNoteDisplay(),
            noteDisplayOverrides = readNoteDisplayOverrides(),
        )
    }

    override fun setInstrument(instrument: Instrument) {
        if (_settings.value.instrument == instrument) return
        _settings.value = _settings.value.copy(instrument = instrument)
        preferences.edit { putString(KEY_INSTRUMENT, instrument.name) }
    }

    override fun setNoteDisplay(noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplay == noteDisplay) return
        _settings.value = _settings.value.copy(noteDisplay = noteDisplay)
        preferences.edit { putString(KEY_NOTE_DISPLAY, noteDisplay.name) }
    }

    override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
        if (_settings.value.noteDisplayOverrides[tool] == noteDisplay) return
        _settings.value = _settings.value.copy(
            noteDisplayOverrides = _settings.value.noteDisplayOverrides + (tool to noteDisplay),
        )
        preferences.edit { putString(overrideKey(tool), noteDisplay.name) }
    }

    private fun readNoteDisplayOverrides(): Map<TrainingTool, NoteDisplay> =
        TrainingTool.entries.mapNotNull { tool ->
            val stored = preferences.getString(overrideKey(tool), null) ?: return@mapNotNull null
            NoteDisplay.entries.firstOrNull { it.name == stored }?.let { tool to it }
        }.toMap()

    private fun overrideKey(tool: TrainingTool) = "${KEY_NOTE_DISPLAY}_${tool.name}"

    private fun readNoteDisplay(): NoteDisplay {
        val stored = preferences.getString(KEY_NOTE_DISPLAY, null) ?: return AppSettings().noteDisplay
        return NoteDisplay.entries.firstOrNull { it.name == stored } ?: AppSettings().noteDisplay
    }

    private fun readInstrument(): Instrument {
        val stored = preferences.getString(KEY_INSTRUMENT, null) ?: return AppSettings().instrument
        return Instrument.entries.firstOrNull { it.name == stored } ?: AppSettings().instrument
    }

    private companion object {
        const val PREFERENCES_NAME = "sax_trainer_settings"
        const val KEY_INSTRUMENT = "instrument"
        const val KEY_NOTE_DISPLAY = "note_display"
    }
}

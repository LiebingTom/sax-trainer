package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.core.content.edit
import com.example.saxtrainer.core.music.NoteNaming
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

    /** Chooses what the note letters are called, everywhere a note name is shown. */
    fun setNoteNaming(noteNaming: NoteNaming)

    /** Sets the default every tool follows unless it has been given its own preference. */
    fun setNoteDisplay(noteDisplay: NoteDisplay)

    /** Gives one tool a preference of its own. */
    fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay)

    /** Sets how many beats are counted off before a practice that has no count-in of its own. */
    fun setCountInBeats(beats: Int)

    /** Chooses whether the count-in is clicked out loud. */
    fun setCountInClick(enabled: Boolean)
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
            noteNaming = readNoteNaming(),
            noteDisplay = readNoteDisplay(),
            noteDisplayOverrides = readNoteDisplayOverrides(),
            countInBeats = readCountInBeats(),
            countInClick = preferences.getBoolean(KEY_COUNT_IN_CLICK, AppSettings().countInClick),
        )
    }

    override fun setInstrument(instrument: Instrument) {
        if (_settings.value.instrument == instrument) return
        _settings.value = _settings.value.copy(instrument = instrument)
        preferences.edit { putString(KEY_INSTRUMENT, instrument.name) }
    }

    override fun setNoteNaming(noteNaming: NoteNaming) {
        if (_settings.value.noteNaming == noteNaming) return
        _settings.value = _settings.value.copy(noteNaming = noteNaming)
        preferences.edit { putString(KEY_NOTE_NAMING, noteNaming.name) }
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

    override fun setCountInBeats(beats: Int) {
        val clamped = beats.coerceAtLeast(0)
        if (_settings.value.countInBeats == clamped) return
        _settings.value = _settings.value.copy(countInBeats = clamped)
        preferences.edit { putInt(KEY_COUNT_IN_BEATS, clamped) }
    }

    override fun setCountInClick(enabled: Boolean) {
        if (_settings.value.countInClick == enabled) return
        _settings.value = _settings.value.copy(countInClick = enabled)
        preferences.edit { putBoolean(KEY_COUNT_IN_CLICK, enabled) }
    }

    private fun readCountInBeats(): Int =
        preferences.getInt(KEY_COUNT_IN_BEATS, AppSettings.DEFAULT_COUNT_IN_BEATS)
            .coerceAtLeast(0)

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

    private fun readNoteNaming(): NoteNaming {
        val stored = preferences.getString(KEY_NOTE_NAMING, null) ?: return AppSettings().noteNaming
        return NoteNaming.entries.firstOrNull { it.name == stored } ?: AppSettings().noteNaming
    }

    private fun readInstrument(): Instrument {
        val stored = preferences.getString(KEY_INSTRUMENT, null) ?: return AppSettings().instrument
        return Instrument.entries.firstOrNull { it.name == stored } ?: AppSettings().instrument
    }

    private companion object {
        const val PREFERENCES_NAME = "sax_trainer_settings"
        const val KEY_INSTRUMENT = "instrument"
        const val KEY_NOTE_NAMING = "note_naming"
        const val KEY_NOTE_DISPLAY = "note_display"
        const val KEY_COUNT_IN_BEATS = "count_in_beats"
        const val KEY_COUNT_IN_CLICK = "count_in_click"
    }
}

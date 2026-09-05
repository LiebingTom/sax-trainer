package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.NoteNaming

/**
 * What a stored setting is called and how a stored string becomes a setting again.
 *
 * Storage itself is per platform - SharedPreferences on Android, `localStorage` on the web - but
 * what is written under which name, and what an unreadable value falls back to, must not be. Two
 * copies of that would be two places to add the next setting to and two chances to disagree about
 * what an install written by a newer version reads as.
 *
 * Every reader below is total: an unknown name, an empty string and a missing value all give the
 * default rather than throwing, because this runs on the way to the first screen.
 */
internal object StoredSettings {

    /** The storage area's own name. Only Android needs one; `localStorage` is per origin already. */
    const val NAME = "sax_trainer_settings"

    const val KEY_INSTRUMENT = "instrument"
    const val KEY_NOTE_NAMING = "note_naming"
    const val KEY_NOTE_DISPLAY = "note_display"
    const val KEY_COUNT_IN_BEATS = "count_in_beats"
    const val KEY_COUNT_IN_CLICK = "count_in_click"

    /** One key per tool, so a tool's own choice never overwrites the shared default. */
    fun overrideKey(tool: TrainingTool): String = "${KEY_NOTE_DISPLAY}_${tool.name}"

    fun instrumentOf(stored: String?): Instrument =
        Instrument.entries.firstOrNull { it.name == stored } ?: AppSettings().instrument

    fun noteNamingOf(stored: String?): NoteNaming =
        NoteNaming.entries.firstOrNull { it.name == stored } ?: AppSettings().noteNaming

    fun noteDisplayOf(stored: String?): NoteDisplay =
        NoteDisplay.entries.firstOrNull { it.name == stored } ?: AppSettings().noteDisplay

    /** Null for a tool that has never been given a preference of its own. */
    fun noteDisplayOverrideOf(stored: String?): NoteDisplay? =
        NoteDisplay.entries.firstOrNull { it.name == stored }

    /**
     * A count-in cannot be negative. Zero can: it is one of the choices the settings screen offers,
     * and it has to be told apart from nothing having been stored, which is why the caller passes
     * null for that rather than a sentinel.
     */
    fun countInBeatsOf(stored: Int?): Int =
        stored?.coerceAtLeast(0) ?: AppSettings.DEFAULT_COUNT_IN_BEATS

    fun countInClickOf(stored: Boolean?): Boolean = stored ?: AppSettings().countInClick
}

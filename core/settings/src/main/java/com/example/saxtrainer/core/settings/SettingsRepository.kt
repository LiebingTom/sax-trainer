package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.coroutines.flow.StateFlow

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

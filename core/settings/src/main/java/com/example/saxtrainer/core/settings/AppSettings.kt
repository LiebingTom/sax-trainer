package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.Transposition

/** Instruments the app can be set to. Adding one is a single entry here. */
enum class Instrument(
    val displayName: String,
    val transposition: Transposition,
) {
    TENOR_SAX("Tenor saxophone", Transposition.B_FLAT),
    ALTO_SAX("Alto saxophone", Transposition.E_FLAT),
    CONCERT("Concert pitch", Transposition.CONCERT),
    ;

    val description: String
        get() = when (transposition) {
            Transposition.CONCERT -> "No transposition, notes sound as written"
            Transposition.B_FLAT -> "Written C sounds a concert B flat"
            Transposition.E_FLAT -> "Written C sounds a concert E flat"
        }
}

/** A training tool that shows notes, and can therefore be told how to show them. */
enum class TrainingTool {
    LONG_TONES,
    FINGERING,
}

/** How a note is presented on screen. */
enum class NoteDisplay(
    val displayName: String,
    val shortName: String,
    val description: String,
) {
    NAME("Note name only", "Name", "Just the letter, e.g. G4"),
    STAFF("Notation only", "Notation", "The note drawn on the staff"),
    BOTH("Name and notation", "Both", "Both together"),
    ;

    val showsName: Boolean get() = this != STAFF

    val showsStaff: Boolean get() = this != NAME
}

/**
 * Application-wide settings shared by every training tool.
 *
 * Kept as one immutable value so future entries (tuning reference, default practice duration,
 * appearance) are one more field here plus one more row in the settings screen.
 */
data class AppSettings(
    val instrument: Instrument = Instrument.TENOR_SAX,
    /** Used by any tool that has not been given a preference of its own. */
    val noteDisplay: NoteDisplay = NoteDisplay.BOTH,
    /** Per-tool choices; a missing entry means the tool follows [noteDisplay]. */
    val noteDisplayOverrides: Map<TrainingTool, NoteDisplay> = emptyMap(),
) {
    /** How [tool] should show notes: its own choice if it has one, otherwise the shared default. */
    fun noteDisplayFor(tool: TrainingTool): NoteDisplay = noteDisplayOverrides[tool] ?: noteDisplay
}

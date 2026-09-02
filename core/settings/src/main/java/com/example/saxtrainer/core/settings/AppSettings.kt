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

/**
 * Application-wide settings shared by every training tool.
 *
 * Kept as one immutable value so future entries (tuning reference, default practice duration,
 * appearance) are one more field here plus one more row in the settings screen.
 */
data class AppSettings(
    val instrument: Instrument = Instrument.TENOR_SAX,
)

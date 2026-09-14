package com.example.saxtrainer.core.settings

/** Whether the app is drawn light or dark. */
enum class ThemeMode(
    val displayName: String,
    val description: String,
) {
    SYSTEM("System default", "Light or dark, following the device"),
    LIGHT("Light", "Always light"),
    DARK("Dark", "Always dark, easier on the eyes in a dim room"),
}

/**
 * The main colour the app's theme is grown from.
 *
 * Only a name here: which colour each one is belongs to the design system, which this module knows
 * nothing about. Adding one is an entry here and its colour where the design system's seeds are.
 */
enum class ThemeColor(val displayName: String) {
    BRASS("Brass"),
    NIGHT_BLUE("Night blue"),
    PLUM("Plum"),
}

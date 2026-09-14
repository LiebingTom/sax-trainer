package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The main colours a theme can be grown from.
 *
 * These are the only colour literals in the app. Each is a seed: [SaxTrainerTheme] derives every
 * role - accent, surfaces, text, outlines, the judgement colours - from whichever one it is given,
 * for light and for dark, so changing a seed here changes the whole app and nothing else needs to.
 * Screens never name a colour; they ask `MaterialTheme.colorScheme`, [FeedbackColors] or
 * [ProgressColors].
 *
 * A seed's hue is what matters. Its lightness is not used, and a seed too grey to read as a colour
 * is given enough chroma to be an accent.
 */
object ThemeSeeds {
    /** Warm gold, the colour of the horn itself. The app's default. */
    val Brass = Color(0xFF7A5A15)

    /** Deep blue, like a lacquered finish. */
    val NightBlue = Color(0xFF2F5D9E)

    /** Dark red-violet. */
    val Plum = Color(0xFF7A3E6E)
}

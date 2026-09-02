package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours that carry a judgement rather than a role.
 *
 * Material's scheme has no "correct" slot, and reusing primary or tertiary would make "you played
 * it" look like ordinary decoration, so the one feedback colour the app needs lives here and is
 * defined for both themes.
 */
object FeedbackColors {

    /** Shown when the player is sounding the note that was asked for. */
    val correct: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) CorrectGreenDark else CorrectGreenLight
}

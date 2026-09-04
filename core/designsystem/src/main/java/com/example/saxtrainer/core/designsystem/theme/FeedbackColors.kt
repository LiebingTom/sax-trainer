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

    /**
     * Shown when an answer was wrong. Material's `error` is about the app failing, not about the
     * player being wrong, and the two should not look the same.
     */
    val incorrect: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) IncorrectRedDark else IncorrectRedLight
}

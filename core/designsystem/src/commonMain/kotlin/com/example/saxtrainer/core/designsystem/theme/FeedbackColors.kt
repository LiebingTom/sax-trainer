package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours that carry a judgement rather than a role.
 *
 * Material's scheme has no "correct" slot, and reusing primary or tertiary would make "you played
 * it" look like ordinary decoration, so the feedback colours live here. Both come from the theme
 * [SaxTrainerTheme] provides, in the mode it was given.
 */
object FeedbackColors {

    /** Shown when the player is sounding the note that was asked for. */
    val correct: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSaxTrainerColors.current.correct

    /**
     * Shown when an answer was wrong, or the note heard is not the one asked for.
     *
     * The same red as Material's `error` today, from the same palette. Kept as its own name because
     * the two mean different things - the player being wrong, the app failing - and should one of
     * them change, call sites must not have to be sorted out again.
     */
    val incorrect: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSaxTrainerColors.current.incorrect
}

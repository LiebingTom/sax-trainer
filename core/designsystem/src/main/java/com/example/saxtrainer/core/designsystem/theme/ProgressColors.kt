package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The colours of the bars at the foot of a practice screen.
 *
 * Named here rather than taken from Material's roles at the call site, so all three can be changed
 * in one place and so the reason each one is that colour is written down next to it.
 */
object ProgressColors {

    /**
     * The bar that matters now - brass, the app's own accent.
     *
     * The brightest thing on the strip, because it is the one that changes what the player does in
     * the next couple of seconds.
     */
    val current: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    /**
     * The session running underneath it - the quiet second accent.
     *
     * Deliberately not brass: the two bars are the same size and sit against each other, so colour
     * is the only thing telling them apart. A neutral reads as context beside the accent rather
     * than as a second thing demanding attention.
     */
    val session: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) SlateDark else SlateLight

    /** What either bar has not filled yet. */
    val track: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer
}

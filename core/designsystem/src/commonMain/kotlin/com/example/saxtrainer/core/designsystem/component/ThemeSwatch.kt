package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.saxTrainerColorScheme

/**
 * What the theme grown from [seed] looks like: its accent inside its soft accent fill.
 *
 * Drawn in the mode currently on screen, so a list of themes previews each one the way it would
 * actually appear, rather than showing the seed itself - which is a single tone and would be too
 * dark to see on a dark screen.
 */
@Composable
fun ThemeSwatch(seed: Color, modifier: Modifier = Modifier) {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    val scheme = remember(seed, darkTheme) { saxTrainerColorScheme(seed, darkTheme) }

    Box(
        modifier = modifier
            .size(Dimens.swatchSize)
            .background(scheme.primaryContainer, CircleShape)
            .padding(Dimens.spaceXs),
    ) {
        Box(Modifier.fillMaxSize().background(scheme.primary, CircleShape))
    }
}

/** Every generated light surface is tone 98 and every dark one tone 6; this sits far from both. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

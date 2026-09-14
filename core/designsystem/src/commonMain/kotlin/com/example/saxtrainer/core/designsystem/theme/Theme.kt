package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Single theme entry point for every screen in the app.
 *
 * [seed] is the theme's main colour and [darkTheme] its mode; every colour on screen is derived from
 * the two (see [ThemeSeeds]). Dynamic colour is deliberately not used so the app keeps its own
 * visual identity.
 *
 * `isSystemInDarkTheme()` is only the default here. Nothing below the theme reads it again, so a
 * caller that overrides [darkTheme] gets that mode everywhere, judgement colours included.
 */
@Composable
fun SaxTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seed: Color = ThemeSeeds.Brass,
    content: @Composable () -> Unit,
) {
    val theme = remember(seed, darkTheme) { colorThemeOf(seed, darkTheme) }
    CompositionLocalProvider(LocalSaxTrainerColors provides theme.colors) {
        MaterialTheme(
            colorScheme = theme.scheme,
            typography = SaxTrainerTypography,
            shapes = SaxTrainerShapes,
            content = content,
        )
    }
}

/**
 * The scheme [SaxTrainerTheme] would provide for [seed] and [darkTheme], without composing it.
 *
 * For drawing a theme other than the current one - a swatch in a list of themes - and for checking
 * what a theme paints.
 */
fun saxTrainerColorScheme(seed: Color, darkTheme: Boolean): ColorScheme =
    colorThemeOf(seed, darkTheme).scheme

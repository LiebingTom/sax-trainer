package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Brass40,
    onPrimary = White,
    primaryContainer = BrassContainerLight,
    onPrimaryContainer = OnBrass,
    secondary = Teal40,
    onSecondary = White,
    secondaryContainer = TealContainerLight,
    tertiary = Copper40,
    onTertiary = White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = Brass80,
    onPrimary = OnBrass,
    primaryContainer = BrassContainerDark,
    onPrimaryContainer = BrassContainerLight,
    secondary = Teal80,
    onSecondary = OnBrass,
    secondaryContainer = TealContainerDark,
    tertiary = Copper80,
    onTertiary = OnBrass,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

/**
 * Single theme entry point for every screen in the app. Dynamic colour is deliberately not used so
 * the app keeps its own visual identity; swapping palettes later only touches this file.
 */
@Composable
fun SaxTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SaxTrainerTypography,
        shapes = SaxTrainerShapes,
        content = content,
    )
}

package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours Material's scheme has no slot for, in the theme they belong to.
 *
 * Provided by [SaxTrainerTheme] next to the colour scheme and chosen by the same `darkTheme`, so a
 * judgement can never be drawn in the other mode's colour.
 */
@Immutable
internal data class SaxTrainerColors(
    /** Shown when the player is sounding the note that was asked for. */
    val correct: Color,
    /** Shown when an answer was wrong. */
    val incorrect: Color,
)

/** Outside a [SaxTrainerTheme] - a bare preview - reads as the default theme in light mode. */
internal val LocalSaxTrainerColors = staticCompositionLocalOf {
    colorThemeOf(ThemeSeeds.Brass, darkTheme = false).colors
}

/** A full theme: Material's scheme and the app's own colours, all grown from one seed. */
@Immutable
internal class ColorTheme(val scheme: ColorScheme, val colors: SaxTrainerColors)

/**
 * Every colour the app draws, derived from [seed] for one mode.
 *
 * All of Material's roles are set - including the ones no screen uses yet - so nothing ever falls
 * back to Material's own baseline purple. The roles take the tones Material 3 assigns them (read off
 * `ColorLightTokens` / `ColorDarkTokens`); only the palettes behind them are the app's.
 */
internal fun colorThemeOf(seed: Color, darkTheme: Boolean): ColorTheme {
    val p = KeyPalettes(seed)
    return if (darkTheme) darkColorTheme(p) else lightColorTheme(p)
}

/**
 * The palettes a theme is made of.
 *
 * Everything that expresses the brand follows [seed]: the accent, a quiet version of it, and the
 * neutrals, which carry a trace of its hue so surfaces and text sit with it rather than against it.
 * The two judgement colours keep a fixed hue - green has to stay green whatever the accent - and are
 * otherwise built the same way, so their tones mirror between light and dark like everything else.
 */
private class KeyPalettes(seed: Color) {
    val primary = TonalPalette.of(seed, minChroma = PRIMARY_MIN_CHROMA)
    val secondary = TonalPalette(primary.hue, SECONDARY_CHROMA)
    val tertiary = TonalPalette((primary.hue + TERTIARY_HUE_SHIFT).mod(360.0), TERTIARY_CHROMA)
    val neutral = TonalPalette(primary.hue, NEUTRAL_CHROMA)
    val neutralVariant = TonalPalette(primary.hue, NEUTRAL_VARIANT_CHROMA)
    val error = TonalPalette(ERROR_HUE, ERROR_CHROMA)
    val correct = TonalPalette(CORRECT_HUE, CORRECT_CHROMA)
}

private fun lightColorTheme(p: KeyPalettes) = ColorTheme(
    scheme = lightColorScheme(
        primary = p.primary.tone(40),
        onPrimary = p.primary.tone(100),
        primaryContainer = p.primary.tone(90),
        onPrimaryContainer = p.primary.tone(10),
        inversePrimary = p.primary.tone(80),
        secondary = p.secondary.tone(40),
        onSecondary = p.secondary.tone(100),
        secondaryContainer = p.secondary.tone(90),
        onSecondaryContainer = p.secondary.tone(10),
        tertiary = p.tertiary.tone(40),
        onTertiary = p.tertiary.tone(100),
        tertiaryContainer = p.tertiary.tone(90),
        onTertiaryContainer = p.tertiary.tone(10),
        background = p.neutral.tone(98),
        onBackground = p.neutral.tone(10),
        surface = p.neutral.tone(98),
        onSurface = p.neutral.tone(10),
        surfaceVariant = p.neutralVariant.tone(90),
        onSurfaceVariant = p.neutralVariant.tone(30),
        surfaceTint = p.primary.tone(40),
        inverseSurface = p.neutral.tone(20),
        inverseOnSurface = p.neutral.tone(95),
        error = p.error.tone(40),
        onError = p.error.tone(100),
        errorContainer = p.error.tone(90),
        onErrorContainer = p.error.tone(10),
        outline = p.neutralVariant.tone(50),
        outlineVariant = p.neutralVariant.tone(80),
        scrim = p.neutral.tone(0),
        surfaceBright = p.neutral.tone(98),
        surfaceContainer = p.neutral.tone(94),
        surfaceContainerHigh = p.neutral.tone(92),
        surfaceContainerHighest = p.neutral.tone(90),
        surfaceContainerLow = p.neutral.tone(96),
        surfaceContainerLowest = p.neutral.tone(100),
        surfaceDim = p.neutral.tone(87),
        primaryFixed = p.primary.tone(90),
        primaryFixedDim = p.primary.tone(80),
        onPrimaryFixed = p.primary.tone(10),
        onPrimaryFixedVariant = p.primary.tone(30),
        secondaryFixed = p.secondary.tone(90),
        secondaryFixedDim = p.secondary.tone(80),
        onSecondaryFixed = p.secondary.tone(10),
        onSecondaryFixedVariant = p.secondary.tone(30),
        tertiaryFixed = p.tertiary.tone(90),
        tertiaryFixedDim = p.tertiary.tone(80),
        onTertiaryFixed = p.tertiary.tone(10),
        onTertiaryFixedVariant = p.tertiary.tone(30),
    ),
    colors = SaxTrainerColors(
        correct = p.correct.tone(40),
        incorrect = p.error.tone(40),
    ),
)

private fun darkColorTheme(p: KeyPalettes) = ColorTheme(
    scheme = darkColorScheme(
        primary = p.primary.tone(80),
        onPrimary = p.primary.tone(20),
        primaryContainer = p.primary.tone(30),
        onPrimaryContainer = p.primary.tone(90),
        inversePrimary = p.primary.tone(40),
        secondary = p.secondary.tone(80),
        onSecondary = p.secondary.tone(20),
        secondaryContainer = p.secondary.tone(30),
        onSecondaryContainer = p.secondary.tone(90),
        tertiary = p.tertiary.tone(80),
        onTertiary = p.tertiary.tone(20),
        tertiaryContainer = p.tertiary.tone(30),
        onTertiaryContainer = p.tertiary.tone(90),
        background = p.neutral.tone(6),
        onBackground = p.neutral.tone(90),
        surface = p.neutral.tone(6),
        onSurface = p.neutral.tone(90),
        surfaceVariant = p.neutralVariant.tone(30),
        onSurfaceVariant = p.neutralVariant.tone(80),
        surfaceTint = p.primary.tone(80),
        inverseSurface = p.neutral.tone(90),
        inverseOnSurface = p.neutral.tone(20),
        error = p.error.tone(80),
        onError = p.error.tone(20),
        errorContainer = p.error.tone(30),
        onErrorContainer = p.error.tone(90),
        outline = p.neutralVariant.tone(60),
        outlineVariant = p.neutralVariant.tone(30),
        scrim = p.neutral.tone(0),
        surfaceBright = p.neutral.tone(24),
        surfaceContainer = p.neutral.tone(12),
        surfaceContainerHigh = p.neutral.tone(17),
        surfaceContainerHighest = p.neutral.tone(22),
        surfaceContainerLow = p.neutral.tone(10),
        surfaceContainerLowest = p.neutral.tone(4),
        surfaceDim = p.neutral.tone(6),
        primaryFixed = p.primary.tone(90),
        primaryFixedDim = p.primary.tone(80),
        onPrimaryFixed = p.primary.tone(10),
        onPrimaryFixedVariant = p.primary.tone(30),
        secondaryFixed = p.secondary.tone(90),
        secondaryFixedDim = p.secondary.tone(80),
        onSecondaryFixed = p.secondary.tone(10),
        onSecondaryFixedVariant = p.secondary.tone(30),
        tertiaryFixed = p.tertiary.tone(90),
        tertiaryFixedDim = p.tertiary.tone(80),
        onTertiaryFixed = p.tertiary.tone(10),
        onTertiaryFixedVariant = p.tertiary.tone(30),
    ),
    colors = SaxTrainerColors(
        correct = p.correct.tone(80),
        incorrect = p.error.tone(80),
    ),
)

// Chroma is OKLCH chroma. The values are chosen so the brass seed reproduces the palette the app had
// before themes were generated: tone 40 of its primary is the old Brass40, tone 98 of its neutral the
// old light surface.

/** A seed greyer than this still gets an accent that reads as a colour. */
private const val PRIMARY_MIN_CHROMA = 0.10
private const val SECONDARY_CHROMA = 0.035
private const val TERTIARY_CHROMA = 0.07
private const val TERTIARY_HUE_SHIFT = 60.0
private const val NEUTRAL_CHROMA = 0.008
private const val NEUTRAL_VARIANT_CHROMA = 0.016

private const val ERROR_HUE = 25.0
private const val ERROR_CHROMA = 0.19
private const val CORRECT_HUE = 145.0
private const val CORRECT_CHROMA = 0.13

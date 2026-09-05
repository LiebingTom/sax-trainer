package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette. Warm brass tones for accents on a deep, low-glare practice-room background.
 * Colours are only referenced from [SaxTrainerTheme]; screens use [MaterialTheme.colorScheme].
 */
internal val Brass40 = Color(0xFF7A5A15)
internal val Brass80 = Color(0xFFE9C46A)
internal val BrassContainerLight = Color(0xFFFFE1A3)
internal val BrassContainerDark = Color(0xFF5A4200)

internal val Teal40 = Color(0xFF1E6E6B)
internal val Teal80 = Color(0xFF7FD5D1)
internal val TealContainerLight = Color(0xFFB6F0EC)
internal val TealContainerDark = Color(0xFF0B4B49)

internal val Copper40 = Color(0xFF8C4A2F)
internal val Copper80 = Color(0xFFFFB59B)

/**
 * The quiet second accent: a neutral that sits beside brass without competing with it.
 *
 * Used where two measures of the same kind are drawn together and one of them is context rather
 * than the thing being watched - the session bar under the note bar.
 */
internal val SlateLight = Color(0xFF79747E)
internal val SlateDark = Color(0xFF938F99)

internal val SurfaceLight = Color(0xFFFDF8F2)
internal val SurfaceDark = Color(0xFF14120F)
internal val OnSurfaceLight = Color(0xFF1D1B17)
internal val OnSurfaceDark = Color(0xFFEBE2D6)

internal val OnBrass = Color(0xFF1F1600)
internal val White = Color(0xFFFFFFFF)

/** Feedback colours that Material's scheme has no slot for. */
internal val CorrectGreenLight = Color(0xFF2E7D32)
internal val CorrectGreenDark = Color(0xFF7BD88F)

internal val IncorrectRedLight = Color(0xFFB3261E)
internal val IncorrectRedDark = Color(0xFFF2B8B5)

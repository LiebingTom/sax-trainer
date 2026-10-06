package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Central spacing and sizing scale. Screens and components must not hard-code dp values so the
 * visual density of the whole app can be tuned from one place.
 */
object Dimens {
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp
    val spaceXl = 32.dp

    val screenPadding = 20.dp
    val cardPadding = 16.dp

    val iconContainerSize = 52.dp
    val iconSize = 26.dp
    val swatchSize = 28.dp

    /** The smallest a favorite tile gets: four to a row on a phone, more on anything wider. */
    val tileMinSize = 80.dp

    val beatIndicatorSize = 20.dp
    val halfBeatIndicatorSize = 10.dp
    val meterHeight = 12.dp
    val spectrumHeight = 160.dp
}

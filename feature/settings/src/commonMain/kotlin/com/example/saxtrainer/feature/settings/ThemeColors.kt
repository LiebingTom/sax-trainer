package com.example.saxtrainer.feature.settings

import androidx.compose.ui.graphics.Color
import com.example.saxtrainer.core.designsystem.theme.ThemeSeeds
import com.example.saxtrainer.core.settings.ThemeColor

/**
 * The design-system seed behind each theme colour a player can choose.
 *
 * Settings only knows the colour by name and the design system only knows seeds; this is where the
 * two meet, used by the settings screen to preview a theme and by the app to apply it.
 */
val ThemeColor.seed: Color
    get() = when (this) {
        ThemeColor.BRASS -> ThemeSeeds.Brass
        ThemeColor.NIGHT_BLUE -> ThemeSeeds.NightBlue
        ThemeColor.PLUM -> ThemeSeeds.Plum
    }

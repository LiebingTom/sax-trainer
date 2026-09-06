package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The same star as [Favorite], hollow: one outline with a second, smaller one wound the other way,
 * which is what makes the middle a hole rather than a second star drawn on top of the first.
 *
 * Drawn by hand; no SVG source yet - see `tools/icons.map`.
 */
val SaxTrainerIcons.FavoriteOff: ImageVector get() = favoriteOff

private val favoriteOff: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "FavoriteOff",
        viewport = 24f,
        "M12,2.4L14.16,9.03L21.13,9.03L15.49,13.13L17.64,19.77L12,15.67" +
            "L6.36,19.77L8.51,13.13L2.87,9.03L9.84,9.03Z" +
            "M11.22,10.93L8.72,10.93L10.75,12.41L9.97,14.79L12,13.32" +
            "L14.03,14.79L13.25,12.41L15.28,10.93L12.78,10.93L12,8.55Z",
    )
}

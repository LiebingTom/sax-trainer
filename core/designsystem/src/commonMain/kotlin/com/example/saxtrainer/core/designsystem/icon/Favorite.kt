package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.Favorite: ImageVector get() = favorite

private val favorite: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "Favorite",
        viewport = 24f,
        "M12,2.4L14.16,9.03L21.13,9.03L15.49,13.13L17.64,19.77L12,15.67" +
            "L6.36,19.77L8.51,13.13L2.87,9.03L9.84,9.03Z",
    )
}

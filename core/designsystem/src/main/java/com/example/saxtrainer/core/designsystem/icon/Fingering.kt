package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.Fingering: ImageVector get() = fingering

private val fingering: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "Fingering",
        viewport = 24f,
        "M10.4,2h3.2v20h-3.2z",
        "M12,5.2m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0",
        "M12,12m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0",
        "M12,18.8m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0",
        "M3.4,9.4h3.4v2.4H3.4z",
    )
}

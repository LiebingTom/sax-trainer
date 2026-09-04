package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.Sequence: ImageVector get() = sequence

private val sequence: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "Sequence",
        viewport = 24f,
        "M4.4,4.6h15.2v2.2H4.4z",
        "M5.2,5.7h1.6v9.4H5.2zM11.2,5.7h1.6v6.4h-1.6zM17.2,5.7h1.6v3.4h-1.6z",
        "M4.2,15.1m-2.4,0a2.4,2.4 0,1 1,4.8 0a2.4,2.4 0,1 1,-4.8 0",
        "M10.2,12.1m-2.4,0a2.4,2.4 0,1 1,4.8 0a2.4,2.4 0,1 1,-4.8 0",
        "M16.2,9.1m-2.4,0a2.4,2.4 0,1 1,4.8 0a2.4,2.4 0,1 1,-4.8 0",
    )
}

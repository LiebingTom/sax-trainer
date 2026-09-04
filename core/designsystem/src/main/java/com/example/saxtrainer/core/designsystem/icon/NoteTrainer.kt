package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.NoteTrainer: ImageVector get() = noteTrainer

private val noteTrainer: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "NoteTrainer",
        viewport = 24f,
        "M6.2,17.4m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0",
        "M8.3,3.8h1.8v13.6H8.3z",
        "M14.1,8.2h1.7v7.6h-1.7z",
        "M17.3,5.8h1.7v12.4h-1.7z",
        "M20.5,9.4h1.7v5.2h-1.7z",
    )
}

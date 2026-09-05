package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.NoteQuiz: ImageVector get() = noteQuiz

private val noteQuiz: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "NoteQuiz",
        viewport = 24f,
        "M4.2,17.4m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0",
        "M5.9,4.6h1.7v12.8H5.9z",
        "M14.6,3.2c-2.2,0 -3.7,1.3 -3.9,3.3h2.1c0.1,-0.9 0.8,-1.5 1.8,-1.5c1,0 1.7,0.6 1.7,1.4c0,0.7 -0.3,1.1 -1.3,1.8c-1.2,0.8 -1.7,1.6 -1.6,3.1h2c0,-0.9 0.2,-1.3 1.2,-2c1.3,-0.9 1.8,-1.7 1.8,-3c0,-1.9 -1.5,-3.1 -3.8,-3.1z",
        "M15.5,13.2m-1.3,0a1.3,1.3 0,1 1,2.6 0a1.3,1.3 0,1 1,-2.6 0",
    )
}

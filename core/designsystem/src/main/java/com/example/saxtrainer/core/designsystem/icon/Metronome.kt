package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Drawn by hand; no SVG source yet - see `tools/icons.map`. */
val SaxTrainerIcons.Metronome: ImageVector get() = metronome

private val metronome: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    saxIcon(
        "Metronome",
        viewport = 24f,
        "M9.4,2h5.2l4.3,19.1c0.14,0.62 -0.33,1.2 -0.97,1.2H6.07c-0.64,0 -1.11,-0.58 -0.97,-1.2L9.4,2zM11,4l-3.5,16.3h9L13,4H11z",
        "M16.6,5.6l1.4,1.4l-8.6,8.6l-1.4,-1.4z",
        "M12,13.6m-1.7,0a1.7,1.7 0,1 1,3.4 0a1.7,1.7 0,1 1,-3.4 0",
    )
}

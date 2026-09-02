package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.fingering.SaxKey

/** How a key is drawn. Not every key is a circle: side and pinky keys are small rounded bars. */
sealed interface KeyShape {
    data object Circle : KeyShape

    data class RoundedRect(val widthRatio: Float, val heightRatio: Float) : KeyShape
}

/**
 * Where one key sits on the diagram and how big it is.
 *
 * Coordinates are fractions of the drawing area, so the layout is resolution independent and the
 * whole diagram scales from a single dimension.
 */
data class KeyPlacement(
    val key: SaxKey,
    val x: Float,
    val y: Float,
    val size: Float,
    val shape: KeyShape = KeyShape.Circle,
)

/**
 * The visual arrangement of the keys, kept apart from the fingering data on purpose: a [Fingering]
 * says which keys are pressed, this says where they are, and the view puts the two together. A
 * prettier or more anatomical diagram later is a change to this file alone.
 *
 * The picture is a simplified front view: left hand above right hand, thumb octave key on the left,
 * side B flat on the right, low C for the left little finger below.
 */
object SaxophoneKeyLayout {

    private const val MAIN_KEY = 0.19f
    private const val SMALL_KEY = 0.12f

    /** Column the six main keys share, as a fraction of the width. */
    const val MAIN_COLUMN_X = 0.5f

    val keys: List<KeyPlacement> = listOf(
        // Left thumb, off to the side of the main column.
        KeyPlacement(SaxKey.OCTAVE, x = 0.18f, y = 0.16f, size = SMALL_KEY),

        // Left hand.
        KeyPlacement(SaxKey.LH1, x = MAIN_COLUMN_X, y = 0.10f, size = MAIN_KEY),
        KeyPlacement(SaxKey.LH2, x = MAIN_COLUMN_X, y = 0.26f, size = MAIN_KEY),
        KeyPlacement(SaxKey.LH3, x = MAIN_COLUMN_X, y = 0.42f, size = MAIN_KEY),

        // Right hand.
        KeyPlacement(SaxKey.RH1, x = MAIN_COLUMN_X, y = 0.62f, size = MAIN_KEY),
        KeyPlacement(SaxKey.RH2, x = MAIN_COLUMN_X, y = 0.78f, size = MAIN_KEY),
        KeyPlacement(SaxKey.RH3, x = MAIN_COLUMN_X, y = 0.94f, size = MAIN_KEY),

        // Side B flat: a bar to the right of the right hand.
        KeyPlacement(
            key = SaxKey.SIDE_BB,
            x = 0.80f,
            y = 0.60f,
            size = SMALL_KEY,
            shape = KeyShape.RoundedRect(widthRatio = 1.6f, heightRatio = 0.7f),
        ),

        // Left little finger, below the left hand.
        KeyPlacement(
            key = SaxKey.LOW_C,
            x = 0.22f,
            y = 0.52f,
            size = SMALL_KEY,
            shape = KeyShape.RoundedRect(widthRatio = 1.4f, heightRatio = 0.8f),
        ),
    )
}

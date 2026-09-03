package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.fingering.SaxKey

/** How a key is drawn. Not every key is a circle: side, palm and pinky keys are rounded bars. */
sealed interface KeyShape {
    data object Circle : KeyShape

    /** A bar, sized relative to the key's diameter. Taller than wide reads as a lever. */
    data class RoundedRect(val widthRatio: Float, val heightRatio: Float) : KeyShape
}

/**
 * Which part of the mechanism a key belongs to.
 *
 * Only used to group the drawing; a fingering never mentions it.
 */
enum class KeyGroup {
    LEFT_HAND,
    LEFT_PINKY,
    PALM,
    RIGHT_HAND,
    SIDE,
    RIGHT_PINKY,
    THUMB,
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
    val group: KeyGroup = KeyGroup.LEFT_HAND,
)

/**
 * The visual arrangement of the keys, kept apart from the fingering data on purpose: a `Fingering`
 * says which keys are pressed, this says where they are, and the view puts the two together.
 *
 * Spacing follows three distances rather than being placed by eye, all taken from the left-hand
 * tone holes on the 120 x 324 panel:
 *
 * - **finger gap** (10.8): between neighbouring tone holes, and between any group of keys and the
 *   tone holes it sits beside.
 * - **group gap** (4.9): between keys within one group - the little-finger table, the side keys,
 *   the palm keys.
 * - **hand gap** (21.6, twice the finger gap): between the last left-hand key and the first
 *   right-hand one.
 *
 * The picture is a front view: left hand above, right hand below, thumb octave key off to the left,
 * palm keys and the little-finger table right of the left hand, side keys and the two F sharp keys
 * left of the right hand, and the two right little-finger spatulas at the bottom.
 */
object SaxophoneKeyLayout {

    private const val MAIN_KEY = 0.19f
    private const val SMALL_KEY = 0.11f
    private const val BAR_KEY = 0.10f

    /** The bis key is the smallest pearl on the instrument. */
    private const val BIS_KEY = 0.08f

    /** Column the six main tone-hole keys share, as a fraction of the width. */
    const val MAIN_COLUMN_X = 0.50f

    /** Where the left hand ends and the right hand begins, halfway across the hand gap. */
    const val HAND_DIVIDER_Y = 0.4657f

    // Columns, as fractions of the width.
    private const val SIDE_COLUMN_X = 0.0788f
    private const val F_SHARP_COLUMN_X = 0.2254f
    private const val SPATULA_X = 0.4350f
    private const val FRONT_F_X = 0.5650f
    private const val PALM_COLUMN_X = 0.8510f
    private const val TABLE_X = 0.8490f

    /** Palm keys and the outer side keys are the same lever. */
    private val PALM_BAR = KeyShape.RoundedRect(widthRatio = 0.75f, heightRatio = 1.5f)
    private val SIDE_BAR = PALM_BAR


    /** The little-finger table: G sharp and low B flat span it, low C sharp and B share a row. */
    private val TABLE_WIDE_BAR = KeyShape.RoundedRect(widthRatio = 1.89f, heightRatio = 0.8f)
    private val TABLE_HALF_BAR = KeyShape.RoundedRect(widthRatio = 0.795f, heightRatio = 0.8f)

    /** High F sharp is the one long lever: as wide as the others, reaching further down. */
    private val HIGH_F_SHARP_BAR = KeyShape.RoundedRect(widthRatio = 0.75f, heightRatio = 1.89f)

    /** The right little-finger spatulas, ending on the centre line. */
    private val SPATULA_BAR = KeyShape.RoundedRect(widthRatio = 0.95f, heightRatio = 0.8f)

    val keys: List<KeyPlacement> = listOf(
        // --- left thumb ---------------------------------------------------------------------
        KeyPlacement(SaxKey.OCTAVE, x = 0.13f, y = 0.0926f, size = SMALL_KEY, group = KeyGroup.THUMB),

        // --- left hand ----------------------------------------------------------------------
        // Front F sits a finger gap above the first tone hole, its left edge on the centre line -
        // the mirror image of the right little finger spatulas.
        KeyPlacement(
            key = SaxKey.FRONT_F,
            x = FRONT_F_X,
            y = 0.0235f,
            size = BAR_KEY,
            shape = SPATULA_BAR,
            group = KeyGroup.LEFT_HAND,
        ),
        KeyPlacement(SaxKey.LH1, x = MAIN_COLUMN_X, y = 0.1250f, size = MAIN_KEY),
        // The bis key is the small pearl between the B and A keys, on the far side of the column.
        KeyPlacement(
            key = SaxKey.BIS,
            x = MAIN_COLUMN_X + 0.155f,
            y = 0.1900f,
            size = BIS_KEY,
            group = KeyGroup.LEFT_HAND,
        ),
        KeyPlacement(SaxKey.LH2, x = MAIN_COLUMN_X, y = 0.2546f, size = MAIN_KEY),
        KeyPlacement(SaxKey.LH3, x = MAIN_COLUMN_X, y = 0.3843f, size = MAIN_KEY),

        // --- palm keys ---------------------------------------------------------------------------
        // One column, a finger gap clear of the bis key, which is what they actually sit beside.
        KeyPlacement(SaxKey.PALM_D, PALM_COLUMN_X, 0.0741f, BAR_KEY, PALM_BAR, KeyGroup.PALM),
        KeyPlacement(SaxKey.PALM_EB, PALM_COLUMN_X, 0.1651f, BAR_KEY, PALM_BAR, KeyGroup.PALM),
        KeyPlacement(SaxKey.PALM_F, PALM_COLUMN_X, 0.2562f, BAR_KEY, PALM_BAR, KeyGroup.PALM),

        // --- left little finger table ----------------------------------------------------------
        // G sharp across the top, low C sharp and B side by side, low B flat across the bottom.
        KeyPlacement(SaxKey.LH_GS, TABLE_X, 0.3472f, BAR_KEY, TABLE_WIDE_BAR, KeyGroup.LEFT_PINKY),
        KeyPlacement(SaxKey.LH_LOW_CS, 0.7740f, 0.4028f, BAR_KEY, TABLE_HALF_BAR, KeyGroup.LEFT_PINKY),
        KeyPlacement(SaxKey.LH_LOW_B, 0.9240f, 0.4028f, BAR_KEY, TABLE_HALF_BAR, KeyGroup.LEFT_PINKY),
        KeyPlacement(SaxKey.LH_LOW_BB, TABLE_X, 0.4583f, BAR_KEY, TABLE_WIDE_BAR, KeyGroup.LEFT_PINKY),

        // --- side keys ---------------------------------------------------------------------------
        // Outer column: high E, side C, side B flat, a group gap apart. Side C is drawn because the
        // instrument has it, though no fingering in the chart presses it.
        KeyPlacement(SaxKey.SIDE_E, SIDE_COLUMN_X, 0.5247f, BAR_KEY, SIDE_BAR, KeyGroup.SIDE),
        KeyPlacement(SaxKey.SIDE_C, SIDE_COLUMN_X, 0.6157f, BAR_KEY, SIDE_BAR, KeyGroup.SIDE),
        KeyPlacement(SaxKey.SIDE_BB, SIDE_COLUMN_X, 0.7068f, BAR_KEY, SIDE_BAR, KeyGroup.SIDE),

        // Inner column, a finger gap from the tone holes: high F sharp - its top level with side
        // B flat - and the F sharp key a group gap below it.
        KeyPlacement(
            key = SaxKey.HIGH_F_SHARP,
            x = F_SHARP_COLUMN_X,
            y = 0.7167f,
            size = BAR_KEY,
            shape = HIGH_F_SHARP_BAR,
            group = KeyGroup.SIDE,
        ),
        KeyPlacement(SaxKey.SIDE_F_SHARP, F_SHARP_COLUMN_X, 0.8177f, BAR_KEY, SIDE_BAR, KeyGroup.SIDE),

        // --- right hand, a hand gap below the left -------------------------------------------------
        KeyPlacement(SaxKey.RH1, MAIN_COLUMN_X, 0.5472f, MAIN_KEY, group = KeyGroup.RIGHT_HAND),
        KeyPlacement(SaxKey.RH2, MAIN_COLUMN_X, 0.6769f, MAIN_KEY, group = KeyGroup.RIGHT_HAND),
        KeyPlacement(SaxKey.RH3, MAIN_COLUMN_X, 0.8065f, MAIN_KEY, group = KeyGroup.RIGHT_HAND),

        // --- right little finger, right edge on the centre line --------------------------------------
        KeyPlacement(SaxKey.RH_EB, SPATULA_X, 0.9082f, BAR_KEY, SPATULA_BAR, KeyGroup.RIGHT_PINKY),
        KeyPlacement(SaxKey.RH_C, SPATULA_X, 0.9637f, BAR_KEY, SPATULA_BAR, KeyGroup.RIGHT_PINKY),
    )

    /** Every key the diagram can draw. */
    val drawnKeys: Set<SaxKey> = keys.map { it.key }.toSet()

    fun placementOf(key: SaxKey): KeyPlacement? = keys.firstOrNull { it.key == key }
}

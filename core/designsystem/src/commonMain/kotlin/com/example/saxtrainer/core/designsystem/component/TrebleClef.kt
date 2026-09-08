package com.example.saxtrainer.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The G clef.
 *
 * The shape itself is in [BravuraGlyphs], traced out of Bravura; this file only says where it goes.
 * There is exactly one measurement that matters and SMuFL has already made it: the glyph's origin
 * is the pitch the clef names, so drawing it at the G line is all it takes to be a G clef, at any
 * size the staff happens to be drawn.
 */

/** Steps the glyph reaches, tail to curl. The staff itself only spans 0..8. */
internal val CLEF_STEP_RANGE = BravuraGlyphs.CLEF_BOTTOM_STEP..BravuraGlyphs.CLEF_TOP_STEP

/** How wide the glyph is, so a block can be sized to hold it. */
internal const val CLEF_WIDTH_SPACES = BravuraGlyphs.CLEF_WIDTH

/** Where the spiral winds to: G4, the second line up. */
private const val G_LINE_STEP = 2

/** A little air on the left, so the clef does not sit against whatever is before it. */
private const val CLEF_INSET_SPACES = 0.35f

/** Where the clef stops, so whatever shares the header block with it knows where to begin. */
internal const val CLEF_ADVANCE_SPACES = CLEF_INSET_SPACES + CLEF_WIDTH_SPACES

/** Draws the clef with its spiral on the G line, starting [leftX] pixels from the left. */
internal fun DrawScope.drawTrebleClef(
    geometry: StaffGeometry,
    color: Color,
    leftX: Float,
) = drawGlyph(
    geometry = geometry,
    path = BravuraGlyphs.gClef,
    color = color,
    x = leftX + CLEF_INSET_SPACES * geometry.spacing,
    y = geometry.y(G_LINE_STEP),
)

package com.example.saxtrainer.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The G clef.
 *
 * Not drawn here: this file holds the answer, and `tools/treble_clef.py` holds the drawing. There
 * the letter is a centreline of Bezier curves and a spiral, swept by a broad nib held at a fixed
 * angle - which is how the shape was made by hand for five hundred years, and why the thick and
 * thin parts fall where they do. The script offsets that stroke into the outline below and can
 * render it, so the glyph is edited by changing a few control points and looking at the result
 * rather than by moving 398 numbers.
 *
 * Regenerate rather than hand-edit:
 *
 *     python tools/treble_clef.py --kotlin
 *
 * The same run writes `tools/treble-clef.svg`, which is the glyph on a staff at the
 * size the app draws it - open that to see what this file says.
 *
 * The outline is in staff spaces, with x running right from the glyph's left edge and y running
 * down from the G line. That is what makes the one measurement that matters true by construction:
 * the spiral winds onto G4, whatever size the staff happens to be drawn at.
 */

/** Steps the glyph reaches, tail to curl. The staff itself only spans 0..8. */
internal val CLEF_STEP_RANGE = -3..11

/** How wide the glyph is, so a block can be sized to hold it. */
internal const val CLEF_WIDTH_SPACES = 2.67f

/** Where the spiral winds to: G4, the second line up. */
private const val G_LINE_STEP = 2

/** A little air on the left, so the clef does not sit against whatever is before it. */
private const val CLEF_INSET_SPACES = 0.35f

/**
 * Draws the clef with its spiral on the G line, starting [leftX] pixels from the left.
 *
 * The outline is parsed once and drawn through a transform, so the shared [Path] is never mutated
 * and the glyph costs nothing to redraw at a new size.
 */
internal fun DrawScope.drawTrebleClef(
    geometry: StaffGeometry,
    color: Color,
    leftX: Float,
) {
    withTransform({
        translate(leftX + CLEF_INSET_SPACES * geometry.spacing, geometry.y(G_LINE_STEP))
        scale(geometry.spacing, geometry.spacing, pivot = Offset.Zero)
    }) {
        drawPath(path = trebleClefPath, color = color)
    }
}

private val trebleClefPath: Path by lazy(LazyThreadSafetyMode.NONE) {
    PathParser().parsePathString(TREBLE_CLEF_PATH_DATA).toPath()
}

private const val TREBLE_CLEF_PATH_DATA =
    "M1.72,-0.04L1.71,-0.01L1.69,0.02L1.67,0.05L1.65,0.07L1.63,0.1L1.61,0.13L1.58,0.16L1.54,0" +
    ".18L1.5,0.19L1.45,0.19L1.41,0.17L1.37,0.15L1.33,0.13L1.29,0.1L1.26,0.07L1.23,0.03L1.2,-0" +
    ".0L1.17,-0.04L1.15,-0.08L1.13,-0.13L1.11,-0.17L1.1,-0.23L1.09,-0.28L1.08,-0.34L1.08,-0.4" +
    "1L1.09,-0.47L1.11,-0.54L1.14,-0.61L1.18,-0.68L1.23,-0.75L1.29,-0.81L1.37,-0.85L1.46,-0.8" +
    "8L1.55,-0.89L1.64,-0.88L1.73,-0.86L1.82,-0.83L1.91,-0.79L1.99,-0.74L2.06,-0.68L2.13,-0.6" +
    "2L2.19,-0.54L2.24,-0.47L2.29,-0.38L2.33,-0.29L2.36,-0.19L2.38,-0.09L2.39,0.02L2.39,0.13L" +
    "2.37,0.24L2.34,0.36L2.3,0.47L2.24,0.58L2.17,0.69L2.08,0.79L1.97,0.87L1.84,0.93L1.71,0.98" +
    "L1.57,1.0L1.43,0.99L1.28,0.97L1.15,0.93L1.01,0.88L0.89,0.81L0.77,0.72L0.66,0.63L0.57,0.5" +
    "2L0.48,0.4L0.41,0.27L0.35,0.13L0.3,-0.01L0.26,-0.14L0.27,-0.23L0.32,-0.34L0.39,-0.46L0.4" +
    "6,-0.59L0.55,-0.71L0.64,-0.83L0.74,-0.95L0.85,-1.07L0.96,-1.19L1.08,-1.31L1.19,-1.42L1.3" +
    ",-1.53L1.42,-1.64L1.53,-1.74L1.63,-1.84L1.72,-1.93L1.81,-2.03L1.9,-2.12L1.97,-2.22L2.04," +
    "-2.32L2.11,-2.42L2.16,-2.52L2.21,-2.63L2.25,-2.74L2.27,-2.85L2.29,-2.97L2.29,-3.09L2.27," +
    "-3.2L2.26,-3.32L2.23,-3.45L2.2,-3.57L2.15,-3.69L2.1,-3.8L2.03,-3.9L1.97,-4.0L1.89,-4.09L" +
    "1.81,-4.17L1.72,-4.24L1.63,-4.3L1.54,-4.34L1.44,-4.38L1.34,-4.4L1.26,-4.41L1.19,-4.4L1.1" +
    "3,-4.38L1.08,-4.35L1.02,-4.33L0.98,-4.31L0.93,-4.29L0.87,-4.27L0.82,-4.23L0.78,-4.18L0.7" +
    "5,-4.12L0.73,-4.05L0.72,-3.98L0.73,-3.91L0.75,-3.83L0.78,-3.75L0.83,-3.68L0.89,-3.61L0.9" +
    "5,-3.56L1.02,-3.52L1.09,-3.48L1.17,-3.45L1.25,-3.43L1.34,-3.42L1.42,-3.41L1.5,-3.41L1.59" +
    ",-3.42L1.56,-3.39L1.55,-3.34L1.56,-3.21L1.58,-3.06L1.59,-2.91L1.6,-2.73L1.61,-2.55L1.62," +
    "-2.35L1.63,-2.15L1.63,-1.93L1.64,-1.7L1.64,-1.47L1.64,-1.22L1.64,-0.97L1.64,-0.7L1.63,-0" +
    ".49L1.63,-0.28L1.62,-0.08L1.62,0.11L1.61,0.3L1.6,0.48L1.6,0.66L1.59,0.82L1.58,0.98L1.58," +
    "1.13L1.57,1.27L1.57,1.4L1.56,1.52L1.56,1.63L1.56,1.71L1.55,1.79L1.54,1.86L1.52,1.94L1.49" +
    ",2.01L1.45,2.08L1.4,2.15L1.34,2.21L1.26,2.25L1.17,2.29L1.07,2.31L0.97,2.32L0.85,2.31L0.7" +
    "3,2.3L0.66,2.29L0.6,2.27L0.55,2.26L0.5,2.23L0.45,2.22L0.41,2.2L0.38,2.19L0.35,2.18L0.32," +
    "2.16L0.29,2.15L0.27,2.13L0.24,2.11L0.22,2.08L0.21,2.05L0.21,2.05L0.2,2.08L0.19,2.12L0.18" +
    ",2.17L0.19,2.21L0.21,2.26L0.24,2.3L0.28,2.34L0.33,2.37L0.38,2.4L0.44,2.42L0.5,2.43L0.58," +
    "2.44L0.65,2.44L0.73,2.44L0.86,2.43L0.98,2.41L1.09,2.37L1.19,2.34L1.28,2.3L1.37,2.26L1.45" +
    ",2.21L1.53,2.16L1.61,2.1L1.68,2.03L1.73,1.94L1.78,1.85L1.81,1.75L1.82,1.65L1.83,1.54L1.8" +
    "4,1.41L1.84,1.28L1.85,1.14L1.85,0.99L1.86,0.83L1.87,0.67L1.87,0.49L1.88,0.31L1.89,0.12L1" +
    ".89,-0.08L1.9,-0.28L1.9,-0.48L1.91,-0.7L1.91,-0.96L1.91,-1.22L1.91,-1.47L1.91,-1.71L1.91" +
    ",-1.94L1.9,-2.16L1.9,-2.37L1.89,-2.56L1.88,-2.75L1.87,-2.93L1.86,-3.09L1.85,-3.24L1.83,-" +
    "3.38L1.78,-3.57L1.59,-3.54L1.52,-3.56L1.45,-3.57L1.38,-3.6L1.32,-3.62L1.26,-3.66L1.21,-3" +
    ".69L1.16,-3.73L1.13,-3.77L1.09,-3.81L1.07,-3.85L1.05,-3.88L1.03,-3.92L1.01,-3.97L1.0,-4." +
    "01L1.0,-4.04L0.99,-4.07L0.98,-4.11L0.98,-4.14L0.98,-4.18L0.99,-4.22L1.01,-4.26L1.05,-4.2" +
    "8L1.09,-4.29L1.14,-4.28L1.19,-4.26L1.23,-4.24L1.28,-4.22L1.35,-4.18L1.42,-4.13L1.49,-4.0" +
    "8L1.55,-4.03L1.62,-3.97L1.68,-3.9L1.73,-3.82L1.78,-3.74L1.83,-3.66L1.88,-3.57L1.91,-3.47" +
    "L1.94,-3.37L1.97,-3.27L1.99,-3.16L2.01,-3.06L2.02,-2.97L2.02,-2.88L2.01,-2.79L1.99,-2.69" +
    "L1.97,-2.6L1.94,-2.5L1.89,-2.41L1.84,-2.31L1.78,-2.21L1.71,-2.11L1.63,-2.01L1.55,-1.92L1" +
    ".45,-1.82L1.35,-1.71L1.23,-1.6L1.12,-1.49L1.0,-1.38L0.88,-1.27L0.77,-1.15L0.65,-1.03L0.5" +
    "4,-0.91L0.44,-0.79L0.34,-0.67L0.25,-0.55L0.17,-0.43L0.09,-0.31L0.0,-0.16L0.01,0.05L0.06," +
    "0.23L0.14,0.41L0.23,0.57L0.35,0.71L0.48,0.83L0.63,0.93L0.78,1.01L0.94,1.07L1.11,1.11L1.2" +
    "7,1.12L1.43,1.11L1.58,1.08L1.73,1.03L1.87,0.98L2.0,0.92L2.12,0.84L2.24,0.76L2.35,0.67L2." +
    "45,0.56L2.53,0.44L2.59,0.31L2.63,0.17L2.66,0.03L2.66,-0.11L2.65,-0.25L2.61,-0.39L2.56,-0" +
    ".51L2.49,-0.63L2.41,-0.73L2.31,-0.82L2.21,-0.89L2.09,-0.95L1.98,-0.98L1.86,-1.0L1.75,-1." +
    "01L1.64,-0.99L1.54,-0.97L1.44,-0.93L1.35,-0.9L1.26,-0.86L1.18,-0.81L1.1,-0.76L1.02,-0.7L" +
    "0.96,-0.63L0.9,-0.55L0.86,-0.47L0.83,-0.38L0.81,-0.28L0.81,-0.19L0.82,-0.1L0.85,-0.01L0." +
    "89,0.06L0.94,0.13L1.0,0.19L1.06,0.24L1.13,0.28L1.21,0.3L1.28,0.31L1.35,0.31L1.41,0.29L1." +
    "47,0.27L1.51,0.24L1.56,0.22L1.6,0.2L1.65,0.17L1.68,0.15L1.71,0.11L1.73,0.07L1.74,0.03L1." +
    "73,-0.01L1.72,-0.04Z"


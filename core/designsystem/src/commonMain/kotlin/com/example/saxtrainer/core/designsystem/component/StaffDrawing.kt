package com.example.saxtrainer.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.conventionalStyle
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.TrebleStaff
import kotlin.math.max
import kotlin.math.min

/**
 * The pieces every staff drawing is made of.
 *
 * Both the single-note glyph and the full sequence draw from here, so a note head, a stem and an
 * accidental look the same wherever they appear and only have to be got right once. Everything is
 * proportional to the staff's line spacing, so the same code draws an inline glyph and a full-width
 * display.
 *
 * This is an intentionally small engraving engine. It draws heads, stems, flags, beams, dots,
 * accidentals, ledger lines and bar lines - enough for the simple exercises the app ships - and it
 * does not draw clefs, key signatures, rests, ties or tuplets. Where a simplification is made, it is
 * named as this engine's rule rather than dressed up as a general one.
 */
internal class StaffGeometry(
    val lowestStep: Int,
    val highestStep: Int,
    /** Pixels per staff space, which is two steps. */
    val spacing: Float,
) {
    val stepHeight: Float = spacing / 2f

    /** Vertical position of a staff step; step 0 is the bottom line of the treble staff. */
    fun y(step: Int): Float = (highestStep - step) * stepHeight

    val lineWidth: Float get() = spacing * LINE_WIDTH_RATIO
    val stemWidth: Float get() = spacing * STEM_WIDTH_RATIO
    val headWidth: Float get() = spacing * HEAD_WIDTH_RATIO
    val headHeight: Float get() = spacing * HEAD_HEIGHT_RATIO
}

/** The five lines, spanning [startX] to [endX]. */
internal fun DrawScope.drawStaffLines(
    geometry: StaffGeometry,
    color: Color,
    startX: Float = 0f,
    endX: Float = size.width,
) {
    for (step in 0..TOP_LINE_STEP step 2) {
        drawLine(
            color = color,
            start = Offset(startX, geometry.y(step)),
            end = Offset(endX, geometry.y(step)),
            strokeWidth = geometry.lineWidth,
        )
    }
}

/** The short lines a note outside the staff sits on. */
internal fun DrawScope.drawLedgerLines(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
) {
    val overhang = geometry.headWidth * LEDGER_OVERHANG
    for (step in placement.ledgerLinesBelow + placement.ledgerLinesAbove) {
        drawLine(
            color = color,
            start = Offset(headX - overhang, geometry.y(step)),
            end = Offset(headX + overhang, geometry.y(step)),
            strokeWidth = geometry.lineWidth,
        )
    }
}

/** A note head, filled for a quarter and shorter, hollow for a half and a whole. */
internal fun DrawScope.drawNoteHead(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    filled: Boolean = true,
) {
    val headY = geometry.y(placement.stepsFromBottomLine)
    val topLeft = Offset(headX - geometry.headWidth / 2f, headY - geometry.headHeight / 2f)
    val headSize = Size(geometry.headWidth, geometry.headHeight)

    // Slightly tilted, as in engraved notation.
    rotate(degrees = HEAD_TILT_DEGREES, pivot = Offset(headX, headY)) {
        if (filled) {
            drawOval(color = color, topLeft = topLeft, size = headSize)
        } else {
            drawOval(
                color = color,
                topLeft = topLeft,
                size = headSize,
                style = Stroke(width = geometry.spacing * HOLLOW_HEAD_STROKE_RATIO),
            )
        }
    }
}

/**
 * Where a stem meets the head, on the side it points to.
 *
 * [stemUp] is separate from the placement's own preference because a beamed group takes one
 * direction for all of its notes, which is not always the direction each of them would pick alone.
 */
internal fun stemX(
    geometry: StaffGeometry,
    headX: Float,
    stemUp: Boolean,
): Float = if (stemUp) headX + geometry.headWidth / 2f else headX - geometry.headWidth / 2f

/** Where a stem of the usual length ends. */
internal fun stemEndY(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    stemUp: Boolean = placement.stemUp,
): Float = if (stemUp) {
    geometry.y(placement.stepsFromBottomLine + STEM_STEPS)
} else {
    geometry.y(placement.stepsFromBottomLine - STEM_STEPS)
}

internal fun DrawScope.drawStem(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    stemUp: Boolean = placement.stemUp,
    endY: Float = stemEndY(geometry, placement, stemUp),
) {
    val x = stemX(geometry, headX, stemUp)
    drawLine(
        color = color,
        start = Offset(x, geometry.y(placement.stepsFromBottomLine)),
        end = Offset(x, endY),
        strokeWidth = geometry.stemWidth,
    )
}

/**
 * The flags on a note that stands alone. A beamed note has none - the beam is the flag - so this is
 * only called for a group of one.
 */
internal fun DrawScope.drawFlags(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    count: Int,
    stemUp: Boolean = placement.stemUp,
    endY: Float = stemEndY(geometry, placement, stemUp),
) {
    if (count <= 0) return
    val x = stemX(geometry, headX, stemUp)
    val direction = if (stemUp) 1f else -1f

    repeat(count) { index ->
        val top = endY + direction * index * geometry.spacing * BEAM_PITCH_RATIO
        val flag = Path().apply {
            moveTo(x, top)
            quadraticTo(
                x + geometry.headWidth * FLAG_REACH,
                top + direction * geometry.spacing * 0.5f,
                x + geometry.headWidth * FLAG_REACH * 0.7f,
                top + direction * geometry.spacing * 1.4f,
            )
        }
        drawPath(path = flag, color = color, style = Stroke(width = geometry.stemWidth * 1.6f))
    }
}

/** Augmentation dots, always to the right of the head and never on a line. */
internal fun DrawScope.drawDots(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    count: Int,
) {
    if (count <= 0) return
    // A dot sits in a space; on a line it is nudged up into the space above, as engraving does.
    val onLine = placement.stepsFromBottomLine % 2 == 0
    val dotY = geometry.y(placement.stepsFromBottomLine) - if (onLine) geometry.stepHeight else 0f

    repeat(count) { index ->
        drawCircle(
            color = color,
            radius = geometry.spacing * DOT_RADIUS_RATIO,
            center = Offset(
                x = headX + geometry.headWidth * (DOT_GAP + index * DOT_PITCH),
                y = dotY,
            ),
        )
    }
}

/**
 * One beam joining a group of notes.
 *
 * The beam is horizontal: every stem in the group is drawn to the same [beamY], which is correct
 * engraving when the group is level and a deliberate simplification when it is not. Slanting a beam
 * to follow the melodic line is a refinement this engine does not attempt yet.
 */
internal fun DrawScope.drawBeam(
    geometry: StaffGeometry,
    color: Color,
    startX: Float,
    endX: Float,
    beamY: Float,
    stemUp: Boolean,
    index: Int,
) {
    val thickness = geometry.spacing * BEAM_THICKNESS_RATIO
    val direction = if (stemUp) 1f else -1f
    val top = beamY + direction * index * geometry.spacing * BEAM_PITCH_RATIO

    drawRect(
        color = color,
        topLeft = Offset(startX, if (stemUp) top else top - thickness),
        size = Size(endX - startX, thickness),
    )
}

/** A bar line, drawn the full height of the staff. Pass [thick] for the double bar at the end. */
internal fun DrawScope.drawBarLine(
    geometry: StaffGeometry,
    color: Color,
    x: Float,
    thick: Boolean = false,
) {
    val width = geometry.lineWidth * if (thick) BAR_LINE_THICK_RATIO else BAR_LINE_RATIO
    drawRect(
        color = color,
        topLeft = Offset(x - width / 2f, geometry.y(TOP_LINE_STEP)),
        size = Size(width, geometry.y(0) - geometry.y(TOP_LINE_STEP)),
    )
}

/** Accidentals are drawn as shapes rather than glyphs, so they never depend on font coverage. */
internal fun DrawScope.drawAccidental(
    geometry: StaffGeometry,
    accidental: Accidental,
    color: Color,
    centerX: Float,
    centerY: Float,
) {
    if (accidental == Accidental.NONE) return

    val spacing = geometry.spacing
    val strokeWidth = spacing * ACCIDENTAL_STROKE_RATIO
    val halfWidth = spacing * 0.3f
    val halfHeight = spacing * 0.75f

    when (accidental) {
        Accidental.SHARP, Accidental.NATURAL -> {
            // Two uprights crossed by two bars; the natural sign simply staggers the uprights.
            val leftTopOffset = if (accidental == Accidental.SHARP) 0f else halfHeight * 0.35f
            val rightBottomOffset = if (accidental == Accidental.SHARP) 0f else halfHeight * 0.35f
            drawLine(
                color = color,
                start = Offset(centerX - halfWidth, centerY - halfHeight + leftTopOffset),
                end = Offset(centerX - halfWidth, centerY + halfHeight),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = color,
                start = Offset(centerX + halfWidth, centerY - halfHeight),
                end = Offset(centerX + halfWidth, centerY + halfHeight - rightBottomOffset),
                strokeWidth = strokeWidth,
            )
            for (bar in listOf(-0.3f, 0.3f)) {
                val tilt = if (accidental == Accidental.SHARP) halfHeight * 0.12f else 0f
                drawLine(
                    color = color,
                    start = Offset(centerX - halfWidth * 1.4f, centerY + halfHeight * bar + tilt),
                    end = Offset(centerX + halfWidth * 1.4f, centerY + halfHeight * bar - tilt),
                    strokeWidth = strokeWidth,
                )
            }
        }

        Accidental.FLAT -> {
            // An upright with a loop hanging off its lower half.
            drawLine(
                color = color,
                start = Offset(centerX - halfWidth, centerY - halfHeight * 1.2f),
                end = Offset(centerX - halfWidth, centerY + halfHeight * 0.8f),
                strokeWidth = strokeWidth,
            )
            val loop = Path().apply {
                moveTo(centerX - halfWidth, centerY + halfHeight * 0.8f)
                quadraticTo(
                    centerX + halfWidth * 1.6f,
                    centerY + halfHeight * 0.2f,
                    centerX - halfWidth,
                    centerY - halfHeight * 0.2f,
                )
            }
            drawPath(path = loop, color = color, style = Stroke(width = strokeWidth))
        }

        Accidental.NONE -> Unit
    }
}

/** Where an accidental's centre goes for a head at [headX]. */
internal fun accidentalX(geometry: StaffGeometry, headX: Float): Float =
    headX - geometry.headWidth * ACCIDENTAL_GAP

// --- extents -----------------------------------------------------------------------------------

/**
 * The vertical room a drawing needs, in staff steps.
 *
 * It has to hold the staff itself plus whatever reaches furthest away from it - a ledger line, an
 * accidental, a stem - and a stem only reaches away from the head on the side it points to.
 */
internal fun extentOf(placements: List<StaffPlacement>): IntRange {
    val lowest = min(
        0,
        placements.minOf { it.stepsFromBottomLine - if (it.stemUp) 1 else STEM_STEPS + 1 },
    )
    val highest = max(
        TOP_LINE_STEP,
        placements.maxOf { it.stepsFromBottomLine + if (it.stemUp) STEM_STEPS + 1 else 1 },
    )
    return lowest..highest
}

/**
 * The room every one of [notes] needs together.
 *
 * Hand the result to [MusicalNoteView] as its `stepRange` and each of those notes is drawn at the
 * same size, on a staff in the same place, so a screen showing one note after another never shifts.
 */
fun staffStepRange(
    notes: List<Note>,
    accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
): IntRange = staffStepRange(notes) { accidentalStyle }

/**
 * The same, where each note brings its own spelling.
 *
 * A B flat and an A sharp are the same pitch on different lines, so how a note is spelled changes
 * how much room the drawing needs.
 */
/**
 * The extent a practice reserves for whichever note it is showing, plus room around it.
 *
 * The notes of one exercise do not all sit at the same height - a C4 hangs a ledger line below the
 * staff where a G4 sits inside it - so a staff sized to the note being shown would change height
 * from note to note and shift everything under it. Sizing it once for the whole exercise fixes
 * that, and the margin leaves room for a note the exercise did not plan for: the pitch the
 * microphone actually heard, drawn beside the one that was asked for.
 */
fun reservedStepRange(
    notes: List<Note>,
    styleOf: (Note) -> AccidentalStyle = Note::conventionalStyle,
    margin: Int = DEFAULT_STAFF_MARGIN,
): IntRange {
    val needed = staffStepRange(notes, styleOf)
    return (needed.first - margin)..(needed.last + margin)
}

/** Four notes of room either side, which is what a detected note usually strays by. */
const val DEFAULT_STAFF_MARGIN = 4

fun staffStepRange(notes: List<Note>, styleOf: (Note) -> AccidentalStyle): IntRange {
    require(notes.isNotEmpty()) { "no notes to make room for" }
    val extents = notes.map { extentOf(listOf(TrebleStaff.placementOf(it, styleOf(it)))) }
    return extents.minOf { it.first }..extents.maxOf { it.last }
}

/** How tall [MusicalNoteView] draws a [stepRange] at [lineSpacing]. */
fun staffHeightOf(stepRange: IntRange, lineSpacing: Dp): Dp =
    lineSpacing * ((stepRange.last - stepRange.first) / 2f)

/** How wide [MusicalNoteView] draws at [lineSpacing], with or without room for a second note. */
fun staffWidthOf(lineSpacing: Dp, reserveSecondaryWidth: Boolean = false): Dp =
    lineSpacing * if (reserveSecondaryWidth) DOUBLE_WIDTH_SPACES else SINGLE_WIDTH_SPACES

// --- proportions ---------------------------------------------------------------------------------

internal const val TOP_LINE_STEP = 8
internal const val STEM_STEPS = 6
internal const val HEAD_WIDTH_RATIO = 1.25f
internal const val HEAD_HEIGHT_RATIO = 0.92f
internal const val HEAD_TILT_DEGREES = -20f
internal const val HOLLOW_HEAD_STROKE_RATIO = 0.13f
internal const val LINE_WIDTH_RATIO = 0.08f
internal const val STEM_WIDTH_RATIO = 0.1f
internal const val LEDGER_OVERHANG = 0.85f
internal const val ACCIDENTAL_GAP = 1.15f
internal const val ACCIDENTAL_STROKE_RATIO = 0.1f
internal const val BEAM_THICKNESS_RATIO = 0.45f
internal const val BEAM_PITCH_RATIO = 0.62f
internal const val FLAG_REACH = 0.9f
internal const val DOT_RADIUS_RATIO = 0.12f
internal const val DOT_GAP = 0.85f
internal const val DOT_PITCH = 0.5f
internal const val BAR_LINE_RATIO = 1.4f
internal const val BAR_LINE_THICK_RATIO = 4f
internal const val SINGLE_WIDTH_SPACES = 4f

/**
 * Room for two notes, kept as close to the single width as their accidentals allow: a second note
 * appearing beside the first should widen the drawing as little as possible.
 */
internal const val DOUBLE_WIDTH_SPACES = 6f

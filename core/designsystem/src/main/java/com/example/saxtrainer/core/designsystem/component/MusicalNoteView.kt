package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.TrebleStaff
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one note - optionally two - in treble staff notation.
 *
 * The notation is generated from the [Note] model, not from per-note images, so any pitch can be
 * shown. A [secondaryNote] is drawn on the same staff next to the primary one in its own colour,
 * which lets a screen show "what was asked for" and "what is being played" at once.
 *
 * Deliberately minimal: one staff, no clef glyph (treble is assumed), no rhythm, no chords. Sizing
 * follows [lineSpacing], so the same component works as a small inline glyph or as a large display.
 *
 * By default the drawing is only as tall as the note needs, which means a high note and a low one
 * come out different sizes. Give [stepRange] - from [staffStepRange] over every note a screen can
 * show - and the staff keeps one size and one position throughout, so nothing moves as the notes
 * change. A note outside that range is then clipped rather than resizing the box, which is the whole
 * point of asking for a fixed one. [reserveSecondaryWidth] does the same for the width, so a second
 * note appearing beside the first does not widen the drawing.
 */
@Composable
fun MusicalNoteView(
    primaryNote: Note,
    modifier: Modifier = Modifier,
    primaryColor: Color = LocalContentColor.current,
    secondaryNote: Note? = null,
    secondaryColor: Color = primaryColor,
    staffColor: Color = primaryColor,
    lineSpacing: Dp = DefaultLineSpacing,
    accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    stepRange: IntRange? = null,
    reserveSecondaryWidth: Boolean = false,
) {
    val primary = TrebleStaff.placementOf(primaryNote, accidentalStyle)
    val secondary = secondaryNote?.let { TrebleStaff.placementOf(it, accidentalStyle) }

    val extent = stepRange ?: extentOf(listOfNotNull(primary, secondary))
    val lowestStep = extent.first
    val highestStep = extent.last

    val height = lineSpacing * ((highestStep - lowestStep) / 2f)
    val width = lineSpacing * if (secondary == null && !reserveSecondaryWidth) {
        SINGLE_WIDTH_SPACES
    } else {
        DOUBLE_WIDTH_SPACES
    }

    val description = buildString {
        append("Treble staff showing ${primaryNote.label}")
        if (secondaryNote != null) append(" and ${secondaryNote.label}")
    }

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val spacing = size.height / ((highestStep - lowestStep) / 2f)
        val stepHeight = spacing / 2f
        fun y(step: Int) = (highestStep - step) * stepHeight

        drawStaff(staffColor, ::y, spacing)

        val headX = if (secondary == null && !reserveSecondaryWidth) {
            size.width * SINGLE_NOTE_X
        } else {
            size.width * PRIMARY_NOTE_X
        }
        drawNote(primary, primaryColor, headX, ::y, spacing)

        if (secondary != null) {
            drawNote(secondary, secondaryColor, size.width * SECONDARY_NOTE_X, ::y, spacing)
        }
    }
}

/**
 * The vertical room a drawing needs, in staff steps.
 *
 * It has to hold the staff itself plus whatever reaches furthest away from it - a ledger line, an
 * accidental, a stem - and a stem only reaches away from the head on the side it points to.
 */
private fun extentOf(placements: List<StaffPlacement>): IntRange {
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
): IntRange {
    require(notes.isNotEmpty()) { "no notes to make room for" }
    val extents = notes.map { extentOf(listOf(TrebleStaff.placementOf(it, accidentalStyle))) }
    return extents.minOf { it.first }..extents.maxOf { it.last }
}

/** How tall [MusicalNoteView] draws a [stepRange] at [lineSpacing]. */
fun staffHeightOf(stepRange: IntRange, lineSpacing: Dp): Dp =
    lineSpacing * ((stepRange.last - stepRange.first) / 2f)

/** How wide [MusicalNoteView] draws at [lineSpacing], with or without room for a second note. */
fun staffWidthOf(lineSpacing: Dp, reserveSecondaryWidth: Boolean = false): Dp =
    lineSpacing * if (reserveSecondaryWidth) DOUBLE_WIDTH_SPACES else SINGLE_WIDTH_SPACES

private fun DrawScope.drawStaff(color: Color, y: (Int) -> Float, spacing: Float) {
    val strokeWidth = spacing * LINE_WIDTH_RATIO
    for (step in 0..TOP_LINE_STEP step 2) {
        drawLine(
            color = color,
            start = Offset(0f, y(step)),
            end = Offset(size.width, y(step)),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawNote(
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    y: (Int) -> Float,
    spacing: Float,
) {
    val headWidth = spacing * HEAD_WIDTH_RATIO
    val headHeight = spacing * HEAD_HEIGHT_RATIO
    val headY = y(placement.stepsFromBottomLine)
    val strokeWidth = spacing * LINE_WIDTH_RATIO

    for (step in placement.ledgerLinesBelow + placement.ledgerLinesAbove) {
        drawLine(
            color = color,
            start = Offset(headX - headWidth * LEDGER_OVERHANG, y(step)),
            end = Offset(headX + headWidth * LEDGER_OVERHANG, y(step)),
            strokeWidth = strokeWidth,
        )
    }

    // Slightly tilted head, as in engraved notation.
    rotate(degrees = HEAD_TILT_DEGREES, pivot = Offset(headX, headY)) {
        drawOval(
            color = color,
            topLeft = Offset(headX - headWidth / 2f, headY - headHeight / 2f),
            size = Size(headWidth, headHeight),
        )
    }

    val stemX = if (placement.stemUp) headX + headWidth / 2f else headX - headWidth / 2f
    val stemEndY = if (placement.stemUp) {
        y(placement.stepsFromBottomLine + STEM_STEPS)
    } else {
        y(placement.stepsFromBottomLine - STEM_STEPS)
    }
    drawLine(
        color = color,
        start = Offset(stemX, headY),
        end = Offset(stemX, stemEndY),
        strokeWidth = spacing * STEM_WIDTH_RATIO,
    )

    drawAccidental(
        accidental = placement.accidental,
        color = color,
        centerX = headX - headWidth * ACCIDENTAL_GAP,
        centerY = headY,
        spacing = spacing,
    )
}

/** Accidentals are drawn as shapes rather than glyphs, so they never depend on font coverage. */
private fun DrawScope.drawAccidental(
    accidental: Accidental,
    color: Color,
    centerX: Float,
    centerY: Float,
    spacing: Float,
) {
    if (accidental == Accidental.NONE) return

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

private val DefaultLineSpacing = 10.dp

private const val TOP_LINE_STEP = 8
private const val STEM_STEPS = 6
private const val HEAD_WIDTH_RATIO = 1.25f
private const val HEAD_HEIGHT_RATIO = 0.92f
private const val HEAD_TILT_DEGREES = -20f
private const val LINE_WIDTH_RATIO = 0.08f
private const val STEM_WIDTH_RATIO = 0.1f
private const val LEDGER_OVERHANG = 0.85f
private const val ACCIDENTAL_GAP = 1.15f
private const val ACCIDENTAL_STROKE_RATIO = 0.1f
private const val SINGLE_WIDTH_SPACES = 4f

/**
 * Room for two notes, kept as close to the single width as their accidentals allow: a second note
 * appearing beside the first should widen the drawing as little as possible.
 */
private const val DOUBLE_WIDTH_SPACES = 6f
private const val SINGLE_NOTE_X = 0.55f
private const val PRIMARY_NOTE_X = 0.33f
private const val SECONDARY_NOTE_X = 0.75f

@Preview(showBackground = true)
@Composable
private fun MusicalNoteViewPreview() {
    SaxTrainerTheme {
        MusicalNoteView(
            primaryNote = Note(NoteName.A, 4),
            primaryColor = Color.Black,
            secondaryNote = Note(NoteName.B, 4),
            secondaryColor = Color.Red,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MusicalNoteViewLedgerPreview() {
    SaxTrainerTheme {
        MusicalNoteView(
            primaryNote = Note(NoteName.C, 6),
            primaryColor = Color.Black,
            secondaryNote = Note(NoteName.C_SHARP, 4),
            secondaryColor = Color.Red,
        )
    }
}

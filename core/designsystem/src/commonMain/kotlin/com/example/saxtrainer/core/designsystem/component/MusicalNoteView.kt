package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.NoteNaming

/**
 * Draws one note - optionally two - in treble staff notation.
 *
 * The notation is generated from the [Note] model, not from per-note images, so any pitch can be
 * shown. A [secondaryNote] is drawn on the same staff next to the primary one in its own colour,
 * which lets a screen show "what was asked for" and "what is being played" at once.
 *
 * Deliberately minimal: one staff, no clef glyph (treble is assumed), no rhythm, no chords. Every
 * note is drawn as a plain stemmed head, because a single note out of context has no written length.
 * A whole sequence, with real note values, is [MusicalStaffView]; both draw from the same primitives
 * in `StaffDrawing.kt`.
 *
 * Sizing follows [lineSpacing], so the same component works as a small inline glyph or as a large
 * display. By default the drawing is only as tall as the note needs, which means a high note and a
 * low one come out different sizes. Give [stepRange] - from [staffStepRange] over every note a
 * screen can show - and the staff keeps one size and one position throughout, so nothing moves as
 * the notes change. A note outside that range is then clipped rather than resizing the box, which is
 * the whole point of asking for a fixed one. [reserveSecondaryWidth] does the same for the width, so
 * a second note appearing beside the first does not widen the drawing.
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
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
) {
    val primary = TrebleStaff.placementOf(primaryNote, accidentalStyle)
    val secondary = secondaryNote?.let { TrebleStaff.placementOf(it, accidentalStyle) }

    val extent = stepRange ?: extentOf(listOfNotNull(primary, secondary))
    val singleNote = secondary == null && !reserveSecondaryWidth

    val height = staffHeightOf(extent, lineSpacing)
    val width = staffWidthOf(lineSpacing, reserveSecondaryWidth = !singleNote)

    val description = buildString {
        append("Treble staff showing ${naming.labelOf(primaryNote, accidentalStyle)}")
        if (secondaryNote != null) {
            append(" and ${naming.labelOf(secondaryNote, accidentalStyle)}")
        }
    }

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val geometry = StaffGeometry(
            lowestStep = extent.first,
            highestStep = extent.last,
            spacing = size.height / ((extent.last - extent.first) / 2f),
        )

        drawStaffLines(geometry, staffColor)

        val headX = size.width * if (singleNote) SINGLE_NOTE_X else PRIMARY_NOTE_X
        drawPlainNote(geometry, primary, primaryColor, headX)

        if (secondary != null) {
            drawPlainNote(geometry, secondary, secondaryColor, size.width * SECONDARY_NOTE_X)
        }
    }
}

/** A head, a stem, its ledger lines and its accidental - no written length, because there is none. */
private fun DrawScope.drawPlainNote(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
) {
    drawLedgerLines(geometry, placement, color, headX)
    drawNoteHead(geometry, placement, color, headX, filled = true)
    drawStem(geometry, placement, color, headX)
    drawAccidental(
        geometry = geometry,
        accidental = placement.accidental,
        color = color,
        centerX = accidentalX(geometry, headX),
        centerY = geometry.y(placement.stepsFromBottomLine),
    )
}

private val DefaultLineSpacing = 10.dp

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

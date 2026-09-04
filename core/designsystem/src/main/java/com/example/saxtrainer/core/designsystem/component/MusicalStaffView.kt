package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.NoteNaming

/**
 * Draws a whole [ToneSequence] as a line of music.
 *
 * Everything visible is derived from the model: a note's written value comes from its duration and
 * the bar's beat unit, its accidental from the sequence's spelling, its beam from where it falls in
 * the beat. Nothing about notation is decided by the caller, which is why the same sequence can be
 * played, shown, and checked without three descriptions of it.
 *
 * [highlightIndex] marks the note being practised now. [noteColor] lets a screen paint an individual
 * note - green for right, something else for wrong - without this component knowing what those
 * colours mean.
 *
 * The engraving is deliberately limited; see `StaffDrawing.kt` for exactly how far it goes. Notably
 * there are no rests, ties, key signatures or clefs, beams are horizontal, and a duration with no
 * single written form is drawn as a plain hollow head rather than guessed at.
 */
@Composable
fun MusicalStaffView(
    sequence: ToneSequence,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    noteColor: (index: Int) -> Color? = { null },
    color: Color = LocalContentColor.current,
    staffColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    lineSpacing: Dp = DefaultSequenceLineSpacing,
    autoScrollToHighlight: Boolean = true,
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
) {
    val layout = remember(sequence) { StaffLayoutBuilder.of(sequence) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val width = lineSpacing * layout.widthSpaces
    val height = staffHeightOf(layout.stepRange, lineSpacing)

    // Keep the note being played in view without the caller having to measure anything.
    LaunchedEffect(highlightIndex, autoScrollToHighlight) {
        if (!autoScrollToHighlight) return@LaunchedEffect
        val note = highlightIndex?.let { index -> layout.notes.getOrNull(index) }
            ?: return@LaunchedEffect
        val headPx = with(density) { (lineSpacing * note.headXSpaces).toPx() }
        val target = (headPx - scrollState.viewportSize / 2f).toInt().coerceAtLeast(0)
        scrollState.animateScrollTo(target)
    }

    val description = sequence.steps.joinToString(" ") {
        naming.labelOf(it.note, sequence.accidentalStyle)
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.size(width = width, height = height)) {
            val geometry = StaffGeometry(
                lowestStep = layout.stepRange.first,
                highestStep = layout.stepRange.last,
                spacing = size.height / ((layout.stepRange.last - layout.stepRange.first) / 2f),
            )
            val space = geometry.spacing

            drawStaffLines(geometry, staffColor)
            layout.barLineXSpaces.forEach { drawBarLine(geometry, staffColor, it * space) }
            drawBarLine(geometry, staffColor, size.width - space * 0.5f, thick = true)

            val stemming = stemmingOf(layout, geometry)

            layout.notes.forEach { note ->
                val paint = noteColor(note.index)
                    ?: if (note.index == highlightIndex) highlightColor else color
                drawNote(geometry, note, paint, stemming[note.index]!!, layout)
            }

            layout.beamGroups.forEach { group ->
                drawBeamGroup(geometry, layout, group, stemming, color, highlightIndex, highlightColor, noteColor)
            }
        }
    }
}

/** How each note's stem is drawn: a beamed group takes one direction and one end for all of them. */
private class Stemming(val stemUp: Boolean, val endY: Float)

private fun stemmingOf(layout: StaffLayout, geometry: StaffGeometry): Map<Int, Stemming> {
    val stemming = mutableMapOf<Int, Stemming>()

    layout.notes.forEach { note ->
        stemming[note.index] = Stemming(
            stemUp = note.placement.stemUp,
            endY = stemEndY(geometry, note.placement),
        )
    }

    layout.beamGroups.forEach { group ->
        val members = layout.notes.filter { it.index in group }
        // One direction for the whole group; the majority wins, as engraving does.
        val ups = members.count { it.placement.stemUp }
        val groupUp = ups * 2 >= members.size
        val ends = members.map { stemEndY(geometry, it.placement, groupUp) }
        // Every stem reaches the same height, so the beam comes out horizontal.
        val beamY = if (groupUp) ends.min() else ends.max()
        members.forEach { stemming[it.index] = Stemming(groupUp, beamY) }
    }

    return stemming
}

private fun DrawScope.drawNote(
    geometry: StaffGeometry,
    note: StaffNotePlacement,
    color: Color,
    stemming: Stemming,
    layout: StaffLayout,
) {
    val headX = note.headXSpaces * geometry.spacing
    val written = note.notated

    drawLedgerLines(geometry, note.placement, color, headX)
    drawNoteHead(
        geometry = geometry,
        placement = note.placement,
        color = color,
        headX = headX,
        // A duration with no written form is drawn hollow: honest about being a held note of no
        // particular value rather than pretending to be a quarter.
        filled = written?.value?.isFilledHead ?: false,
    )

    if (written == null || written.value.hasStem) {
        drawStem(geometry, note.placement, color, headX, stemming.stemUp, stemming.endY)
    }

    // A beamed note has no flag: the beam is the flag.
    if (written != null && layout.beamGroupOf(note.index) == null) {
        drawFlags(
            geometry = geometry,
            placement = note.placement,
            color = color,
            headX = headX,
            count = written.value.beamCount,
            stemUp = stemming.stemUp,
            endY = stemming.endY,
        )
    }

    drawDots(geometry, note.placement, color, headX, written?.dots ?: 0)
    drawAccidental(
        geometry = geometry,
        accidental = note.placement.accidental,
        color = color,
        centerX = accidentalX(geometry, headX),
        centerY = geometry.y(note.placement.stepsFromBottomLine),
    )
}

private fun DrawScope.drawBeamGroup(
    geometry: StaffGeometry,
    layout: StaffLayout,
    group: IntRange,
    stemming: Map<Int, Stemming>,
    color: Color,
    highlightIndex: Int?,
    highlightColor: Color,
    noteColor: (Int) -> Color?,
) {
    val members = layout.notes.filter { it.index in group }
    if (members.size < 2) return

    val first = members.first()
    val last = members.last()
    val stem = stemming[first.index] ?: return
    val startX = stemX(geometry, first.headXSpaces * geometry.spacing, stem.stemUp)
    val endX = stemX(geometry, last.headXSpaces * geometry.spacing, stem.stemUp)

    // The beam belongs to the whole group, so it takes the group's colour: highlighted if the note
    // being played is one of them, otherwise plain.
    val beamColor = if (highlightIndex != null && highlightIndex in group) {
        noteColor(highlightIndex) ?: highlightColor
    } else {
        color
    }

    // As many beams as the shortest note in the group asks for.
    val beams = members.maxOf { it.notated?.value?.beamCount ?: 0 }
    repeat(beams) { index ->
        drawBeam(geometry, beamColor, startX, endX, stem.endY, stem.stemUp, index)
    }
}

private val DefaultSequenceLineSpacing = 12.dp

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MusicalStaffViewScalePreview() {
    val scale = ToneSequence(
        steps = listOf(
            NoteName.C to 4, NoteName.D to 4, NoteName.E to 4, NoteName.F to 4,
            NoteName.G to 4, NoteName.A to 4, NoteName.B to 4,
        ).map { ToneStep(Note(it.first, it.second), NotatedDuration(NoteValue.QUARTER).beats()) } +
            ToneStep(Note(NoteName.C, 5), NotatedDuration(NoteValue.HALF).beats()),
    )
    SaxTrainerTheme {
        MusicalStaffView(sequence = scale, highlightIndex = 2)
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MusicalStaffViewRhythmPreview() {
    val rhythm = ToneSequence(
        steps = listOf(
            ToneStep(Note(NoteName.C, 4), NotatedDuration(NoteValue.QUARTER, dots = 1).beats()),
            ToneStep(Note(NoteName.D, 4), NotatedDuration(NoteValue.EIGHTH).beats()),
            ToneStep(Note(NoteName.E, 4), NotatedDuration(NoteValue.EIGHTH).beats()),
            ToneStep(Note(NoteName.F, 4), NotatedDuration(NoteValue.EIGHTH).beats()),
            ToneStep(Note(NoteName.G, 4), NotatedDuration(NoteValue.QUARTER).beats()),
        ),
    )
    SaxTrainerTheme {
        MusicalStaffView(sequence = rhythm, highlightIndex = 3)
    }
}

package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.TrebleStaff
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
 * colours mean. [noteStyle] says, in the same per-note way, which notes are written as their name
 * instead of drawn; the design system never learns what a display setting is.
 *
 * [prefix] is the block at the head of the staff - the clef, and one day a key signature. Its width
 * is fixed whatever is in it, so turning the clef off does not move a note.
 *
 * **This cannot be asked for an intrinsic measurement.** It sizes the music against the width it is
 * offered and is therefore built on `BoxWithConstraints`, which throws when a parent asks it how
 * tall it would like to be - so it must not be put inside a `Modifier.height(IntrinsicSize.Min)`
 * row or anything else that measures intrinsics.
 *
 * The engraving is deliberately limited; see `StaffDrawing.kt` for exactly how far it goes. Notably
 * there are no key signatures, beams are horizontal, and a duration with no single written form is
 * drawn as a plain hollow head rather than guessed at.
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
    prefix: StaffPrefix = StaffPrefix.None,
    noteStyle: (index: Int) -> StaffNoteStyle = { StaffNoteStyle.HEAD },
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    labelBackground: Color? = null,
    /** Runs the staff lines out to the full width available, with the music kept at its own size. */
    fillWidth: Boolean = false,
    /**
     * The double bar that closes the music.
     *
     * Worth turning off where the line is not a piece of music but a single note held up to be
     * read: a bar line says "this is the end", and a target note is not an end of anything.
     */
    closingBar: Boolean = true,
    /**
     * The vertical room to keep, whatever this sequence needs.
     *
     * A screen that shows one note after another passes [reservedStepRange] over every note it can
     * show, and the staff then keeps one size and one position throughout instead of resizing as
     * the notes change.
     */
    stepRange: IntRange? = null,
    /**
     * Keeps room for an accidental beside every note, whether it has one or not.
     *
     * A practice showing one note at a time out of a pool wants this: without it the note moves
     * sideways as the pitch changes from C to C sharp. A written-out piece does not.
     */
    reserveAccidentals: Boolean = false,
    /**
     * Centres the music in the width left over after the header block.
     *
     * The block itself does not move - it is part of the staff, not of the music - so what is
     * centred is everything after it. Only has an effect where the music is narrower than the
     * space available, which is exactly when a line does not scroll.
     */
    centerMusic: Boolean = false,
    /**
     * A second note to show beside the one at that index, in [secondaryColor].
     *
     * For the pitch a microphone actually heard, held up against the one that was asked for. It has
     * no duration and takes no part in the line: a head, its accidental and its ledger lines, and
     * nothing else.
     */
    secondaryNote: (index: Int) -> Note? = { null },
    secondaryColor: Color = staffColor,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val styles = sequence.steps.indices.map(noteStyle)
    // Every written name is given the room of the widest name in the sequence, not its own, so the
    // line does not reflow when one note's presentation changes. The same rule [NoteSlot] follows.
    val labelWidthSpaces = remember(sequence, styles, labelStyle, lineSpacing, naming, density) {
        if (styles.none { it == StaffNoteStyle.NAME } || sequence.notes.isEmpty()) {
            0f
        } else {
            widestLabelSpaces(sequence, measurer, labelStyle, naming, lineSpacing, density)
        }
    }

    val layout = remember(sequence, prefix, styles, labelWidthSpaces, reserveAccidentals) {
        StaffLayoutBuilder.of(sequence, prefix, styles, labelWidthSpaces, reserveAccidentals)
    }
    val scrollState = rememberScrollState()

    val extent = (stepRange ?: layout.stepRange).including(prefix.stepRange)
    val musicWidth = lineSpacing * layout.widthSpaces
    val height = staffHeightOf(extent, lineSpacing)

    // Keep the note being played in view without the caller having to measure anything.
    LaunchedEffect(highlightIndex, autoScrollToHighlight) {
        if (!autoScrollToHighlight) return@LaunchedEffect
        // By step, not by position: the first head of the note being played is what to scroll to.
        val note = highlightIndex?.let { index -> layout.notes.firstOrNull { it.index == index } }
            ?: return@LaunchedEffect
        val headPx = with(density) { (lineSpacing * note.headXSpaces).toPx() }
        val target = (headPx - scrollState.viewportSize / 2f).toInt().coerceAtLeast(0)
        scrollState.animateScrollTo(target)
    }

    val description = sequence.steps.joinToString(" ") { step ->
        step.note?.let { naming.labelOf(it, sequence.accidentalStyle) } ?: "rest"
    }

    // Outside the scroll on purpose: inside it the width offered is infinite and says nothing.
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        // A parent that offers unbounded width - a Row without a weight, say - has no edge to run
        // to, and asking for one would size the canvas at infinity.
        val width = if (fillWidth && maxWidth.isFinite) maxOf(musicWidth, maxWidth) else musicWidth

        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.size(width = width, height = height)) {
                val geometry = StaffGeometry(
                    lowestStep = extent.first,
                    highestStep = extent.last,
                    spacing = size.height / ((extent.last - extent.first) / 2f),
                )
                val space = geometry.spacing

                drawStaffLines(geometry, staffColor)
                // The clef and the time signature take the notes' colour rather than the staff's.
                // They are writing, not ruling: the lines are the paper a note is put on, while a
                // clef is a symbol somebody wrote there, and reading them as one ink says so.
                if (prefix.clef) drawTrebleClef(geometry, color, leftX = 0f)
                prefix.timeSignature?.let { signature ->
                    val x = if (prefix.clef) CLEF_ADVANCE_SPACES + TIME_SIGNATURE_GAP_SPACES else 0f
                    drawTimeSignature(geometry, signature, color, leftX = x * space)
                }
                // The line that closes the header block. It is what makes the block read as a
                // block, and it is why a clef appearing or a key signature arriving later cannot
                // look like the music has shifted: the music starts at this line, always.
                if (layout.prefixWidthSpaces > 0f) {
                    drawBarLine(geometry, staffColor, layout.prefixWidthSpaces * space)
                }

                // Everything from here on is the music, which is the only part that is centred. The
                // header block above stays where it is: it belongs to the staff, not to the line.
                val slack = size.width - layout.widthSpaces * space
                val musicX = if (centerMusic && slack > 0f) slack / 2f else 0f

                translate(left = musicX) {
                    layout.barLineXSpaces.forEach { drawBarLine(geometry, staffColor, it * space) }
                    // At the end of the music, which is not the edge of the canvas once it fills
                    // the width: the lines carry on past the closing bar, as an unfinished line does.
                    if (closingBar) {
                        drawBarLine(
                            geometry = geometry,
                            color = staffColor,
                            x = (layout.widthSpaces - 0.5f) * space,
                            style = BarLineStyle.FINAL,
                        )
                    }

                    val stemming = stemmingOf(layout, geometry)

                    // Position, not step: a note held across a bar line is written as more than one
                    // head, so the page has its own numbering. The step index is what a caller
                    // colours and highlights by, so both heads of a tie answer to the same one.
                    layout.notes.forEachIndexed { position, note ->
                        val paint = noteColor(note.index)
                            ?: if (note.index == highlightIndex) highlightColor else color
                        when {
                            note.isRest -> drawRest(
                                geometry = geometry,
                                notated = note.notated,
                                color = paint,
                                centerX = note.headXSpaces * space,
                            )

                            note.style == StaffNoteStyle.HEAD ->
                                drawNote(geometry, note, paint, stemming[position]!!, position, layout)

                            else -> drawNoteLabel(
                                geometry = geometry,
                                measurer = measurer,
                                // A rest is never written as a name - the layout draws it as a
                                // rest - so there is always a pitch here to name.
                                text = sequence.steps[note.index].note
                                    ?.let { naming.labelOf(it, sequence.accidentalStyle) }
                                    .orEmpty(),
                                style = labelStyle,
                                color = paint,
                                background = labelBackground,
                                centerX = note.headXSpaces * space,
                                step = note.placement.stepsFromBottomLine,
                            )
                        }

                        if (note.tiedToNext) {
                            val next = layout.notes[position + 1]
                            drawTie(
                                geometry = geometry,
                                color = paint,
                                fromX = note.headXSpaces * space,
                                toX = next.headXSpaces * space,
                                step = note.placement.stepsFromBottomLine,
                                // Away from the stems, which both point the same way here: the two
                                // heads are the same pitch, so they take the same direction.
                                below = stemming[position]!!.stemUp,
                            )
                        }

                        // Only beside the head the note starts on: one reading of one pitch, not
                        // one for every head a held note happens to be written with.
                        val heard = if (note.isRest || note.continuesPrevious(layout, position)) {
                            null
                        } else {
                            secondaryNote(note.index)
                        }
                        heard?.let {
                            drawSecondaryNote(
                                geometry = geometry,
                                note = it,
                                accidentalStyle = sequence.accidentalStyle,
                                color = secondaryColor,
                                headX = (note.headXSpaces + SECONDARY_NOTE_GAP_SPACES) * space,
                            )
                        }
                    }

                    layout.beamGroups.forEach { group ->
                        drawBeamGroup(geometry, layout, group, stemming, color, highlightIndex, highlightColor, noteColor)
                    }
                }
            }
        }
    }
}

/**
 * How much room the longest name in [sequence] needs, in staff spaces.
 *
 * Measured rather than guessed, because a name's width is a font's business; converted to staff
 * spaces because that is the only unit the layout deals in.
 */
private fun widestLabelSpaces(
    sequence: ToneSequence,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    naming: NoteNaming,
    lineSpacing: Dp,
    density: Density,
): Float {
    val widestPx = sequence.steps.mapNotNull { it.note }.maxOf {
        val label = naming.labelOf(it, sequence.accidentalStyle)
        measurer.measure(AnnotatedString(label), labelStyle).size.width
    }
    val spacingPx = with(density) { lineSpacing.toPx() }
    val padding = spacingPx * LABEL_PADDING_RATIO * 2f
    return (widestPx + padding) / spacingPx + LABEL_GAP_SPACES
}

/** Air between one written name and the next, so two of them never touch. */
private const val LABEL_GAP_SPACES = 0.4f

/** Between the clef and the time signature that follows it, inside the same fixed block. */
private const val TIME_SIGNATURE_GAP_SPACES = 0.5f

/**
 * From a note's head to the head of the pitch heard beside it.
 *
 * Far enough that the second note's own accidental clears the first note's head, and near enough
 * that the pair still reads as one note being compared with another rather than as two notes.
 */
private const val SECONDARY_NOTE_GAP_SPACES = 2.6f

/**
 * A pitch shown beside the note it is being compared with: no stem, no value, no place in the line.
 *
 * Drawn filled because it is a reading rather than a written note - a hollow head would say "hold
 * this for two beats", which is not what a microphone reporting a pitch means.
 */
private fun DrawScope.drawSecondaryNote(
    geometry: StaffGeometry,
    note: Note,
    accidentalStyle: AccidentalStyle,
    color: Color,
    headX: Float,
) {
    val placement = TrebleStaff.placementOf(note, accidentalStyle)
    drawLedgerLines(geometry, placement, color, headX)
    drawNoteHead(geometry, placement, color, headX, filled = true)
    drawAccidental(
        geometry = geometry,
        accidental = placement.accidental,
        color = color,
        rightX = accidentalRightX(geometry, headX),
        centerY = geometry.y(placement.stepsFromBottomLine),
    )
}

/** How each note's stem is drawn: a beamed group takes one direction and one end for all of them. */
private class Stemming(val stemUp: Boolean, val endY: Float)

/** Keyed by position in [StaffLayout.notes], because one step can be written as several heads. */
private fun stemmingOf(layout: StaffLayout, geometry: StaffGeometry): Map<Int, Stemming> {
    val stemming = mutableMapOf<Int, Stemming>()

    layout.notes.forEachIndexed { position, note ->
        stemming[position] = Stemming(
            stemUp = note.placement.stemUp,
            endY = stemEndY(geometry, note.placement),
        )
    }

    layout.beamGroups.forEach { group ->
        val members = group.map { layout.notes[it] }
        // One direction for the whole group; the majority wins, as engraving does.
        val ups = members.count { it.placement.stemUp }
        val groupUp = ups * 2 >= members.size
        val ends = members.map { stemEndY(geometry, it.placement, groupUp) }
        // Every stem reaches the same height, so the beam comes out horizontal.
        val beamY = if (groupUp) ends.min() else ends.max()
        group.forEach { stemming[it] = Stemming(groupUp, beamY) }
    }

    return stemming
}

/** True where this head is the far end of a tie rather than a note of its own. */
private fun StaffNotePlacement.continuesPrevious(layout: StaffLayout, position: Int): Boolean =
    position > 0 && layout.notes[position - 1].let { it.tiedToNext && it.index == index }

private fun DrawScope.drawNote(
    geometry: StaffGeometry,
    note: StaffNotePlacement,
    color: Color,
    stemming: Stemming,
    position: Int,
    layout: StaffLayout,
) {
    val headX = note.headXSpaces * geometry.spacing
    val written = note.notated
    val whole = written?.value == NoteValue.WHOLE

    drawLedgerLines(geometry, note.placement, color, headX, headWidthOf(whole))
    drawNoteHead(
        geometry = geometry,
        placement = note.placement,
        color = color,
        headX = headX,
        // A duration with no written form is drawn hollow: honest about being a held note of no
        // particular value rather than pretending to be a quarter.
        filled = written?.value?.isFilledHead ?: false,
        whole = whole,
    )

    if (written == null || written.value.hasStem) {
        drawStem(geometry, note.placement, color, headX, stemming.stemUp, stemming.endY)
    }

    // A beamed note has no flag: the beam is the flag.
    if (written != null && layout.beamGroupOf(position) == null) {
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
        rightX = accidentalRightX(geometry, headX),
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
    val members = group.map { layout.notes[it] }
    if (members.size < 2) return

    val first = members.first()
    val last = members.last()
    val stem = stemming[first.index] ?: return
    val startX = stemX(geometry, first.headXSpaces * geometry.spacing, stem.stemUp)
    val endX = stemX(geometry, last.headXSpaces * geometry.spacing, stem.stemUp)

    // The beam belongs to the whole group, so it takes the group's colour: highlighted if the note
    // being played is one of them, otherwise plain.
    val beamColor = if (highlightIndex != null && members.any { it.index == highlightIndex }) {
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
        MusicalStaffView(
            sequence = scale,
            highlightIndex = 2,
            prefix = StaffPrefix.Treble,
            fillWidth = true,
        )
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
        MusicalStaffView(sequence = rhythm, highlightIndex = 3, prefix = StaffPrefix.Treble)
    }
}

/** Some notes drawn and some written, which is what a mixed presentation looks like on one line. */
@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MusicalStaffViewNamesPreview() {
    val line = ToneSequence(
        steps = listOf(
            NoteName.C to 4, NoteName.E to 4, NoteName.A_SHARP to 4, NoteName.G to 4,
        ).map { ToneStep(Note(it.first, it.second), NotatedDuration(NoteValue.QUARTER).beats()) },
    )
    SaxTrainerTheme {
        MusicalStaffView(
            sequence = line,
            prefix = StaffPrefix.Treble,
            fillWidth = true,
            noteStyle = { index -> if (index % 2 == 0) StaffNoteStyle.HEAD else StaffNoteStyle.NAME },
            labelBackground = MaterialTheme.colorScheme.surface,
        )
    }
}

package com.example.saxtrainer.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.conventionalStyle
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.TimeSignature
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
 * accidentals, ledger lines, bar lines, rests, ties and the clef - enough for the simple exercises
 * the app ships. It does not draw key signatures or tuplets. Where a simplification is made,
 * it is named as this engine's rule rather than dressed up as a general one.
 *
 * The letterforms - heads, flags, accidentals, dots, bar lines, the clef - are outlines in
 * [BravuraGlyphs], traced from a music font at build time and drawn here as plain paths. No font is
 * loaded at runtime. What is left in this file is the part that is proportion rather than shape:
 * lines, stems, beams, and where each glyph goes. Those proportions are Bravura's own engraving
 * defaults, so the drawn parts and the traced parts agree about how heavy a line should be.
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
}

/**
 * Draws one traced outline with its own origin put at ([x], [y]).
 *
 * The glyphs are in staff spaces, so the only thing between an outline and the screen is the
 * staff's own scale. The shared [Path] is never touched: the transform is applied to the canvas
 * around it, which is what lets one parsed outline be drawn at any size for the life of the app.
 */
internal fun DrawScope.drawGlyph(
    geometry: StaffGeometry,
    path: Path,
    color: Color,
    x: Float,
    y: Float,
) {
    withTransform({
        translate(x, y)
        scale(geometry.spacing, geometry.spacing, pivot = Offset.Zero)
    }) {
        drawPath(path = path, color = color)
    }
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

/**
 * The short lines a note outside the staff sits on.
 *
 * They reach the same distance past the head whatever head it is, so [headWidthSpaces] has to be
 * the width of the head actually being drawn - a whole note is wider than the rest.
 */
internal fun DrawScope.drawLedgerLines(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    headWidthSpaces: Float = BravuraGlyphs.NOTEHEAD_WIDTH,
) {
    val overhang =
        geometry.spacing * (headWidthSpaces / 2f + BravuraGlyphs.LEDGER_LINE_EXTENSION)
    for (step in placement.ledgerLinesBelow + placement.ledgerLinesAbove) {
        drawLine(
            color = color,
            start = Offset(headX - overhang, geometry.y(step)),
            end = Offset(headX + overhang, geometry.y(step)),
            strokeWidth = geometry.spacing * BravuraGlyphs.LEDGER_LINE_THICKNESS,
        )
    }
}

/**
 * A note head, filled for a quarter and shorter, hollow for a half and a whole.
 *
 * [headX] is the middle of the head, as it has always been; the outline is placed by its own left
 * edge, so it is shifted back by half its width. A whole note has a head of its own, wider than the
 * others and without the opening a stem would meet - hence [whole] as well as [filled].
 */
internal fun DrawScope.drawNoteHead(
    geometry: StaffGeometry,
    placement: StaffPlacement,
    color: Color,
    headX: Float,
    filled: Boolean = true,
    whole: Boolean = false,
) {
    val glyph = when {
        filled -> BravuraGlyphs.noteheadBlack
        whole -> BravuraGlyphs.noteheadWhole
        else -> BravuraGlyphs.noteheadHalf
    }
    drawGlyph(
        geometry = geometry,
        path = glyph,
        color = color,
        x = headX - headWidthOf(whole) * geometry.spacing / 2f,
        y = geometry.y(placement.stepsFromBottomLine),
    )
}

/** How wide the head of a note is, in staff spaces. */
internal fun headWidthOf(whole: Boolean): Float =
    if (whole) BravuraGlyphs.NOTEHEAD_WHOLE_WIDTH else BravuraGlyphs.NOTEHEAD_WIDTH

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
    // Not from the middle of the head: a hollow head would show the stem through it. The font's own
    // anchor says where a stem meets a head, and that is a little off centre, on the outline.
    val attach = BravuraGlyphs.STEM_ATTACH_SPACES * geometry.spacing
    val startY = geometry.y(placement.stepsFromBottomLine) + if (stemUp) -attach else attach
    drawLine(
        color = color,
        start = Offset(x, startY),
        end = Offset(x, endY),
        strokeWidth = geometry.stemWidth,
    )
}

/**
 * The flags on a note that stands alone. A beamed note has none - the beam is the flag - so this is
 * only called for a group of one.
 *
 * One outline carries however many flags the note has, so [count] chooses a shape rather than being
 * drawn that many times. This engine writes nothing shorter than a sixteenth, so two is the most
 * there is; anything longer is drawn with the sixteenth's pair rather than silently dropped.
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
    val single = count == 1
    val glyph = when {
        stemUp && single -> BravuraGlyphs.flag8thUp
        stemUp -> BravuraGlyphs.flag16thUp
        single -> BravuraGlyphs.flag8thDown
        else -> BravuraGlyphs.flag16thDown
    }
    val attach = when {
        stemUp && single -> BravuraGlyphs.FLAG_8_UP_ATTACH
        stemUp -> BravuraGlyphs.FLAG_16_UP_ATTACH
        single -> BravuraGlyphs.FLAG_8_DOWN_ATTACH
        else -> BravuraGlyphs.FLAG_16_DOWN_ATTACH
    }

    // A flag hangs off the side of the stem the stem itself is drawn on, so it starts at the
    // stem's own edge rather than its centre line, at the height the font's anchor asks for.
    drawGlyph(
        geometry = geometry,
        path = glyph,
        color = color,
        x = stemX(geometry, headX, stemUp) - geometry.stemWidth / 2f,
        y = endY - attach * geometry.spacing,
    )
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
        drawGlyph(
            geometry = geometry,
            path = BravuraGlyphs.augmentationDot,
            color = color,
            x = headX + geometry.headWidth * (DOT_GAP + index * DOT_PITCH) -
                BravuraGlyphs.DOT_WIDTH * geometry.spacing / 2f,
            y = dotY,
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

/**
 * The tie joining one head to the next: the same note, still sounding.
 *
 * Drawn on the side away from the stems, from just clear of one head to just clear of the other, so
 * it never runs into a stem or a bar line it crosses - and it does cross bar lines, which is the
 * whole point of it. Thick in the middle and drawn to nothing at both ends, as an engraved tie is:
 * two curves of slightly different height with the space between them filled.
 */
internal fun DrawScope.drawTie(
    geometry: StaffGeometry,
    color: Color,
    fromX: Float,
    toX: Float,
    step: Int,
    below: Boolean,
) {
    val spacing = geometry.spacing
    val direction = if (below) 1f else -1f
    val startX = fromX + geometry.headWidth / 2f + spacing * TIE_END_GAP
    val endX = toX - geometry.headWidth / 2f - spacing * TIE_END_GAP
    if (endX <= startX) return

    val y = geometry.y(step) + direction * spacing * TIE_LIFT
    val midX = (startX + endX) / 2f
    // A quadratic reaches half its control height, so the control point is twice the rise wanted.
    val outer = y + direction * spacing * (TIE_RISE * 2f + BravuraGlyphs.TIE_MIDPOINT_THICKNESS)
    val inner = y + direction * spacing * TIE_RISE * 2f

    val tie = Path().apply {
        moveTo(startX, y)
        quadraticTo(midX, outer, endX, y)
        quadraticTo(midX, inner, startX, y)
        close()
    }
    drawPath(path = tie, color = color)
}

/**
 * A rest, centred on [centerX].
 *
 * Which shape says how long the silence is, exactly as a head's shape says how long a note is, so
 * this reads the written value and nothing else. A duration with no single written form is drawn as
 * a quarter rest: the same honesty the heads have, where an unwritable length is drawn as the plain
 * shape rather than guessed at with dots and flags.
 *
 * Rests are registered on the middle line, which is where they are drawn - except the whole rest,
 * which hangs under the line above it, as it does on any printed page.
 */
internal fun DrawScope.drawRest(
    geometry: StaffGeometry,
    notated: NotatedDuration?,
    color: Color,
    centerX: Float,
) {
    val (glyph, width) = when (notated?.value) {
        NoteValue.WHOLE -> BravuraGlyphs.restWhole to BravuraGlyphs.REST_WHOLE_WIDTH
        NoteValue.HALF -> BravuraGlyphs.restHalf to BravuraGlyphs.REST_HALF_WIDTH
        NoteValue.EIGHTH -> BravuraGlyphs.rest8th to BravuraGlyphs.REST_8TH_WIDTH
        NoteValue.SIXTEENTH -> BravuraGlyphs.rest16th to BravuraGlyphs.REST_16TH_WIDTH
        NoteValue.QUARTER, null -> BravuraGlyphs.restQuarter to BravuraGlyphs.REST_QUARTER_WIDTH
    }
    val step = if (notated?.value == NoteValue.WHOLE) WHOLE_REST_STEP else REST_STEP
    drawGlyph(
        geometry = geometry,
        path = glyph,
        color = color,
        x = centerX - width * geometry.spacing / 2f,
        y = geometry.y(step),
    )
}

/** Which of the three bar lines is meant. */
internal enum class BarLineStyle {
    /** One thin line: the end of a bar. */
    SINGLE,

    /** Two thin lines: the end of a section. Nothing asks for one yet. */
    DOUBLE,

    /** Thin then thick: the end of the music. */
    FINAL,
}

/**
 * A bar line, drawn the full height of the staff and centred on [x].
 *
 * Centred rather than hung to the left of [x] because that is where the layout has always put one,
 * and a final bar line that shifted the end of the music would be a layout change wearing a
 * drawing's clothes.
 */
internal fun DrawScope.drawBarLine(
    geometry: StaffGeometry,
    color: Color,
    x: Float,
    style: BarLineStyle = BarLineStyle.SINGLE,
) {
    val (glyph, width) = when (style) {
        BarLineStyle.SINGLE -> BravuraGlyphs.barlineSingle to BravuraGlyphs.BAR_LINE_SINGLE_WIDTH
        BarLineStyle.DOUBLE -> BravuraGlyphs.barlineDouble to BravuraGlyphs.BAR_LINE_DOUBLE_WIDTH
        BarLineStyle.FINAL -> BravuraGlyphs.barlineFinal to BravuraGlyphs.BAR_LINE_FINAL_WIDTH
    }
    drawGlyph(
        geometry = geometry,
        path = glyph,
        color = color,
        x = x - width * geometry.spacing / 2f,
        // The glyph stands on the bottom line and reaches the four spaces up to the top one.
        y = geometry.y(0),
    )
}

/**
 * An accidental, hung to the left of the head it belongs to.
 *
 * Placed by its right edge, not its middle: a flat is narrower than a sharp, and aligning their
 * centres would leave the flat further from its note than the sharp is from its own. [rightX] is
 * where the accidental ends and the gap before the head begins.
 */
internal fun DrawScope.drawAccidental(
    geometry: StaffGeometry,
    accidental: Accidental,
    color: Color,
    rightX: Float,
    centerY: Float,
) {
    val (glyph, width) = when (accidental) {
        Accidental.NONE -> return
        Accidental.SHARP -> BravuraGlyphs.accidentalSharp to BravuraGlyphs.SHARP_WIDTH
        Accidental.FLAT -> BravuraGlyphs.accidentalFlat to BravuraGlyphs.FLAT_WIDTH
        Accidental.NATURAL -> BravuraGlyphs.accidentalNatural to BravuraGlyphs.NATURAL_WIDTH
    }
    drawGlyph(
        geometry = geometry,
        path = glyph,
        color = color,
        x = rightX - width * geometry.spacing,
        y = centerY,
    )
}

/**
 * A time signature, its two numbers stacked in the staff.
 *
 * Each digit is two staff spaces tall and centred on a line, so the top number fills the upper half
 * of the staff and the bottom number the lower half - which is why they are drawn at steps 6 and 2
 * and need no measuring. The two are centred on each other, since "12" over "8" is the usual case
 * of one being wider than the other.
 *
 * Nothing here knows 4/4 from 6/8: both numbers are read off [TimeSignature] a digit at a time.
 */
internal fun DrawScope.drawTimeSignature(
    geometry: StaffGeometry,
    timeSignature: TimeSignature,
    color: Color,
    leftX: Float,
) {
    val top = timeSignature.beatsPerBar.toString()
    val bottom = timeSignature.beatUnit.toString()
    val width = maxOf(digitsWidth(top), digitsWidth(bottom))

    drawDigits(geometry, top, color, leftX + (width - digitsWidth(top)) / 2f * geometry.spacing,
        TIME_SIGNATURE_TOP_STEP)
    drawDigits(geometry, bottom, color,
        leftX + (width - digitsWidth(bottom)) / 2f * geometry.spacing,
        TIME_SIGNATURE_BOTTOM_STEP)
}

private fun DrawScope.drawDigits(
    geometry: StaffGeometry,
    digits: String,
    color: Color,
    leftX: Float,
    step: Int,
) {
    var x = leftX
    digits.forEach { character ->
        val digit = character - '0'
        drawGlyph(
            geometry = geometry,
            path = BravuraGlyphs.timeSignatureDigits[digit],
            color = color,
            x = x,
            y = geometry.y(step),
        )
        x += BravuraGlyphs.timeSignatureDigitWidths[digit] * geometry.spacing
    }
}

private fun digitsWidth(digits: String): Float =
    digits.sumOf { BravuraGlyphs.timeSignatureDigitWidths[it - '0'].toDouble() }.toFloat()

/** How much room a time signature takes, in staff spaces. */
internal fun timeSignatureWidthSpaces(timeSignature: TimeSignature): Float = maxOf(
    digitsWidth(timeSignature.beatsPerBar.toString()),
    digitsWidth(timeSignature.beatUnit.toString()),
)

/** The middle line: where a rest is registered, and so where its glyph is anchored. */
private const val REST_STEP = 4

/** The fourth line, which a whole rest hangs under rather than sits on. */
private const val WHOLE_REST_STEP = 6

/** The line each number is centred on: the staff's upper half, then its lower half. */
private const val TIME_SIGNATURE_TOP_STEP = 6
private const val TIME_SIGNATURE_BOTTOM_STEP = 2

/** Where an accidental ends, for a head at [headX]: just short of the head's left edge. */
internal fun accidentalRightX(geometry: StaffGeometry, headX: Float): Float =
    headX - geometry.headWidth / 2f - geometry.spacing * ACCIDENTAL_GAP_SPACES

/**
 * A note's name, written where its head would have been.
 *
 * The background is what makes it readable: five staff lines running through three letters is not
 * something a reader should have to look past. Pass null for it where the staff behind the name is
 * not in the way.
 */
internal fun DrawScope.drawNoteLabel(
    geometry: StaffGeometry,
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    background: Color?,
    centerX: Float,
    step: Int,
) {
    val measured = measurer.measure(AnnotatedString(text), style)
    val width = measured.size.width.toFloat()
    val height = measured.size.height.toFloat()
    val topLeft = Offset(centerX - width / 2f, geometry.y(step) - height / 2f)

    if (background != null) {
        val padding = geometry.spacing * LABEL_PADDING_RATIO
        drawRoundRect(
            color = background,
            topLeft = Offset(topLeft.x - padding, topLeft.y - padding),
            size = Size(width + padding * 2f, height + padding * 2f),
            cornerRadius = CornerRadius(geometry.spacing * LABEL_CORNER_RATIO),
        )
    }

    drawText(textLayoutResult = measured, color = color, topLeft = topLeft)
}

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
        placements.minOf { it.stepsFromBottomLine - if (it.stemUp) 1 else STEM_STEPS },
    )
    val highest = max(
        TOP_LINE_STEP,
        placements.maxOf { it.stepsFromBottomLine + if (it.stemUp) STEM_STEPS else 1 },
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
/**
 * How far a stem reaches from its head, in steps: three and a half staff spaces.
 *
 * The length is not free. A flag is one shape drawn for a stem of the usual length, so a
 * shorter stem would pull the flag's tail down over the head it belongs to.
 */
internal const val STEM_STEPS = 7
/**
 * The width of an ordinary head, which is also the distance between the two sides a stem can be
 * drawn on, and so the unit several other placements are measured in.
 */
internal const val HEAD_WIDTH_RATIO = BravuraGlyphs.NOTEHEAD_WIDTH

// The drawn parts take their weight from the same font the outlines came from, so a stem and a
// flag, or a staff line and a bar line, are not two people's idea of how heavy a line should be.
internal const val LINE_WIDTH_RATIO = BravuraGlyphs.STAFF_LINE_THICKNESS
internal const val STEM_WIDTH_RATIO = BravuraGlyphs.STEM_THICKNESS
internal const val BEAM_THICKNESS_RATIO = BravuraGlyphs.BEAM_THICKNESS
internal const val BEAM_PITCH_RATIO = BravuraGlyphs.BEAM_THICKNESS + BravuraGlyphs.BEAM_SPACING

/**
 * Air between an accidental and the head it belongs to.
 *
 * Wider than an engraver would set it. These are read at a glance while playing rather than studied
 * on paper, and at the sizes the app draws, a sharp that merely comes close to its head reads as
 * touching it. The room the layout already reserves covers this, so nothing moves.
 */
internal const val ACCIDENTAL_GAP_SPACES = 0.45f

/** How far a tie starts from the head it leaves, how far it sits off it, and how high it arches. */
internal const val TIE_END_GAP = 0.2f
internal const val TIE_LIFT = 0.55f
internal const val TIE_RISE = 0.35f
internal const val DOT_GAP = 0.85f
internal const val DOT_PITCH = 0.5f
internal const val LABEL_PADDING_RATIO = 0.18f
internal const val LABEL_CORNER_RATIO = 0.3f
internal const val SINGLE_WIDTH_SPACES = 4f

/**
 * Room for two notes, kept as close to the single width as their accidentals allow: a second note
 * appearing beside the first should widen the drawing as little as possible.
 */
internal const val DOUBLE_WIDTH_SPACES = 6f

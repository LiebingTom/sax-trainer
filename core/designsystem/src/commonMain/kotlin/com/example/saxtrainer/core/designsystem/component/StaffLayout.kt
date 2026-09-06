package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.beamGroups
import com.example.saxtrainer.core.music.notatedDurationOf
import kotlin.math.pow

/** How one note of a line is shown. */
enum class StaffNoteStyle {
    /** Drawn: a head, and whatever stem, flag, dots and beam its written length asks for. */
    HEAD,

    /**
     * Written: the note's name, on a background, where the head would have been.
     *
     * Nothing else - no head, no stem, no flag, no dot, and no part in a beam. A note shown as its
     * name is out of notation, and out of notation a note has no written length; the same reasoning
     * that makes [MusicalNoteView] draw a plain head with no value.
     */
    NAME,
}

/**
 * The block at the head of a staff: the clef today, a key signature or a time signature later.
 *
 * Its width is a property of the block, not of what happens to be in it. A staff with a clef and a
 * staff without one put their first note in exactly the same place, which is what stops a line of
 * music moving when the clef is turned off - and what will stop it moving when a key signature is
 * turned on, as long as a screen widens the block once for the whole line rather than per note.
 *
 * A bar line closes the block wherever it has any width, so the reader can see where the fixed part
 * of the staff ends and the music begins.
 */
data class StaffPrefix(
    val clef: Boolean = true,
    val widthSpaces: Float = DEFAULT_WIDTH_SPACES,
) {
    init {
        require(widthSpaces >= 0f) { "a block cannot be $widthSpaces spaces wide" }
    }

    /** Vertical room the block needs of its own: a treble clef reaches past both outer lines. */
    val stepRange: IntRange? get() = if (clef) CLEF_STEP_RANGE else null

    companion object {
        /**
         * The clef takes about three spaces of this, counting the air in front of it, and the rest
         * is the gap before the music. That leftover is where a key signature or a time signature
         * will stand; one that wants more room than is left is a wider block, decided once for the
         * line, which moves the music once rather than note by note.
         */
        const val DEFAULT_WIDTH_SPACES = 4f

        /** A treble clef, and room for what will one day stand beside it. */
        val Treble = StaffPrefix()

        /** No block at all: an inline glyph or a small answer option wants its space back. */
        val None = StaffPrefix(clef = false, widthSpaces = 0f)

        /** As wide as [Treble] but empty, for hiding the clef without moving a single note. */
        val Reserved = StaffPrefix(clef = false)
    }
}

/** The range that holds both, where the second one is a range at all. */
internal fun IntRange.including(other: IntRange?): IntRange =
    if (other == null) this else minOf(first, other.first)..maxOf(last, other.last)

/** One note, placed. Positions are in staff spaces, so the layout is independent of any screen. */
data class StaffNotePlacement(
    val index: Int,
    /** Centre of the note head, or of the name written in its place, in staff spaces. */
    val headXSpaces: Float,
    val placement: StaffPlacement,
    /** How the note is written, or null when no single head says it - a five-beat tone, say. */
    val notated: NotatedDuration?,
    val style: StaffNoteStyle = StaffNoteStyle.HEAD,
)

/**
 * Where everything on a line of music goes, worked out without a canvas.
 *
 * Kept apart from the drawing so it can be reasoned about and tested on its own, and so a screen can
 * ask where a note is - to scroll to it - without measuring anything.
 */
data class StaffLayout(
    val notes: List<StaffNotePlacement>,
    /**
     * Bar lines, in staff spaces. The final double bar is not among them; it closes the width.
     *
     * A bar line is drawn before the first note that begins at or after the bar boundary. Where a
     * note starts exactly on the boundary - which is every note of the material this app ships -
     * that is exactly right. Where a note spans the boundary, engraved music would split it with a
     * tie; this engine does not write ties, so it draws the line before that note instead, which is
     * as close as it can come without pretending to be right.
     */
    val barLineXSpaces: List<Float>,
    /** Room kept at the head of the staff for the clef and whatever joins it. */
    val prefixWidthSpaces: Float,
    /** Where the music ends, which is where the closing double bar goes - not the screen edge. */
    val widthSpaces: Float,
    val stepRange: IntRange,
    val beamGroups: List<IntRange>,
) {
    /** The group [index] belongs to, or null when it stands alone and keeps its flag. */
    fun beamGroupOf(index: Int): IntRange? = beamGroups.firstOrNull { index in it }
}

/**
 * Turns a sequence into positions.
 *
 * Horizontal room grows with duration but not in proportion to it: a half note is worth more space
 * than an eighth, though not four times as much, or a bar of short notes would be unreadable next to
 * one long one. The exponent below is the usual engraving compromise, not a rule of music.
 */
object StaffLayoutBuilder {

    /**
     * @param prefix the block at the head of the staff, which every note is pushed clear of.
     * @param styles how each note is shown; an empty list means every one of them is drawn.
     * @param labelWidthSpaces room one written name takes. The same for every name on the line,
     *   measured from the widest of them, so that a line does not reflow when one note's
     *   presentation changes - the rule [rememberNoteSlot] already follows for a single note.
     */
    fun of(
        sequence: ToneSequence,
        prefix: StaffPrefix = StaffPrefix.None,
        styles: List<StaffNoteStyle> = emptyList(),
        labelWidthSpaces: Float = 0f,
    ): StaffLayout {
        val placements = sequence.steps.map {
            TrebleStaff.placementOf(it.note, sequence.accidentalStyle)
        }
        val styleOf = { index: Int -> styles.getOrElse(index) { StaffNoteStyle.HEAD } }

        val notes = mutableListOf<StaffNotePlacement>()
        val barLines = mutableListOf<Float>()
        var cursor = prefix.widthSpaces + LEADING_SPACES

        val beatsPerBar = sequence.timeSignature.beatsPerBar.toDouble()
        var nextBarBeat = beatsPerBar

        sequence.steps.indices.forEach { index ->
            val placement = placements[index]
            val notated = sequence.notatedDurationOf(index)
            val startBeat = sequence.startBeatOf(index).value

            if (index > 0 && startBeat >= nextBarBeat - BEAT_TOLERANCE) {
                barLines += cursor
                cursor += BAR_LINE_SPACES
                // A note long enough to swallow a whole bar leaves only one line, not several.
                while (nextBarBeat <= startBeat + BEAT_TOLERANCE) nextBarBeat += beatsPerBar
            }

            val style = styleOf(index)
            // A written name carries its own accidental in its letters and has no dots to hang off
            // it, so it needs neither of those rooms - only a box of its own to sit in the middle of.
            val accidentalRoom = when {
                style == StaffNoteStyle.NAME -> 0f
                placement.accidental == com.example.saxtrainer.core.music.Accidental.NONE -> 0f
                else -> ACCIDENTAL_SPACES
            }

            notes += StaffNotePlacement(
                index = index,
                headXSpaces = if (style == StaffNoteStyle.NAME) {
                    cursor + labelWidthSpaces / 2f
                } else {
                    cursor + accidentalRoom + HEAD_OFFSET_SPACES
                },
                placement = placement,
                notated = notated,
                style = style,
            )

            val dots = notated?.dots ?: 0
            val advance = advanceSpaces(sequence.steps[index].duration.value)
            cursor += if (style == StaffNoteStyle.NAME) {
                // Never narrower than the name itself, and never narrower than the note's length
                // asks for, so a line of names still reads left to right in time.
                maxOf(labelWidthSpaces, advance)
            } else {
                accidentalRoom + advance + dots * DOT_SPACES
            }
        }

        // Beams reach past the end of a stem, so the drawing needs a little more room than the notes
        // strictly ask for; padding the range keeps every note the same size whatever is beamed.
        val extent = staffStepRange(sequence.notes, sequence.accidentalStyle)
        // A written name is taller than the head it stands in for, and a clef reaches past both
        // outer lines. Either one clipped is the easiest way to get this wrong, so both are asked.
        val nameRoom = if (notes.any { it.style == StaffNoteStyle.NAME }) LABEL_HEADROOM_STEPS else 0
        val stepRange = ((extent.first - BEAM_HEADROOM_STEPS - nameRoom)..
            (extent.last + BEAM_HEADROOM_STEPS + nameRoom)).including(prefix.stepRange)

        return StaffLayout(
            notes = notes,
            barLineXSpaces = barLines,
            prefixWidthSpaces = prefix.widthSpaces,
            widthSpaces = cursor + TRAILING_SPACES,
            stepRange = stepRange,
            beamGroups = beamGroupsOf(sequence, styleOf),
        )
    }

    /**
     * The beams, with the written names taken out of them.
     *
     * A name has no stem for a beam to reach, so it does not merely go undrawn - it breaks the group
     * it was in. What is left either side of it is beamed if two notes are still standing there,
     * and flagged if only one is. Done here rather than in the drawing so that the answer can be
     * checked without a canvas.
     */
    private fun beamGroupsOf(
        sequence: ToneSequence,
        styleOf: (Int) -> StaffNoteStyle,
    ): List<IntRange> = sequence.beamGroups().flatMap { group ->
        if (group.all { styleOf(it) == StaffNoteStyle.HEAD }) return@flatMap listOf(group)

        val runs = mutableListOf<IntRange>()
        var start: Int? = null
        // One past the end, so a run that reaches the end of the group is closed like any other.
        for (index in group.first..group.last + 1) {
            val drawn = index <= group.last && styleOf(index) == StaffNoteStyle.HEAD
            val open = start
            when {
                drawn && open == null -> start = index
                !drawn && open != null -> {
                    if (index - 1 > open) runs += open..index - 1
                    start = null
                }
            }
        }
        runs
    }

    /** Room one note takes, in staff spaces, for a duration of [beats]. */
    private fun advanceSpaces(beats: Double): Float =
        (BASE_ADVANCE + DURATION_ADVANCE * beats.pow(DURATION_EXPONENT)).toFloat()

    private const val LEADING_SPACES = 1.2f
    private const val TRAILING_SPACES = 1.4f
    private const val BAR_LINE_SPACES = 0.8f
    private const val ACCIDENTAL_SPACES = 1.4f
    private const val HEAD_OFFSET_SPACES = 0.75f
    private const val DOT_SPACES = 0.6f
    private const val BASE_ADVANCE = 1.6
    private const val DURATION_ADVANCE = 2.6
    private const val DURATION_EXPONENT = 0.7
    private const val BEAM_HEADROOM_STEPS = 2

    /** A name written on the line it belongs to overhangs it; one step either side covers that. */
    private const val LABEL_HEADROOM_STEPS = 1

    /** Beats are doubles; a boundary should not be missed because of the last bit of one. */
    private const val BEAT_TOLERANCE = 1e-9
}

package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.NotatedNote
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.beamGroups
import com.example.saxtrainer.core.music.notatedReading
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
    /** Shown after the clef when there is one. Null leaves the room empty rather than narrower. */
    val timeSignature: TimeSignature? = null,
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

        /**
         * Wide enough for a clef and a time signature beside it.
         *
         * One width for every time signature, sized for the widest this app will meet, so that
         * 4/4 and 12/8 put their first note in the same place - the same reason the block has a
         * fixed width in the first place.
         */
        const val TIMED_WIDTH_SPACES = 7f

        /** A treble clef, and room for what will one day stand beside it. */
        val Treble = StaffPrefix()

        /** No block at all: an inline glyph or a small answer option wants its space back. */
        val None = StaffPrefix(clef = false, widthSpaces = 0f)

        /** As wide as [Treble] but empty, for hiding the clef without moving a single note. */
        val Reserved = StaffPrefix(clef = false)

        /** A treble clef with a time signature after it, in a block wide enough for either. */
        fun treble(timeSignature: TimeSignature?): StaffPrefix = when (timeSignature) {
            null -> Treble
            else -> StaffPrefix(
                clef = true,
                timeSignature = timeSignature,
                widthSpaces = TIMED_WIDTH_SPACES,
            )
        }
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
    /** How this head is written, or null where even one piece of it has no single written value. */
    val notated: NotatedDuration?,
    val style: StaffNoteStyle = StaffNoteStyle.HEAD,
    /**
     * True where this is a silence rather than a note.
     *
     * A rest still has a [placement] - the line it is registered on - because that is where it is
     * drawn, and it still takes its room in the line, because that is what a rest is for. What it
     * has no part in is anything that belongs to a pitch: no accidental, no stem, no beam, no tie.
     */
    val isRest: Boolean = false,
    /**
     * True when the head after this one is the same sounding note still going.
     *
     * A held note that crosses a bar line is written as two heads with a tie between them, so
     * several placements can share one [index]: the index says which note of the exercise this
     * belongs to, and this says whether the reader is meant to play it again or hold it.
     */
    val tiedToNext: Boolean = false,
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
     * A bar line is drawn before the first head that begins at or after the bar boundary. A held
     * note that runs past one has already been cut there by [notatedReading] into two heads joined
     * by a tie, so the line appears inside the held note rather than being hidden by it - the metre
     * is a fact about the music and does not stop being true because a note is still sounding.
     */
    val barLineXSpaces: List<Float>,
    /** Room kept at the head of the staff for the clef and whatever joins it. */
    val prefixWidthSpaces: Float,
    /** Where the music ends, which is where the closing double bar goes - not the screen edge. */
    val widthSpaces: Float,
    val stepRange: IntRange,
    val beamGroups: List<IntRange>,
) {
    /**
     * The group the head at [position] belongs to, or null when it stands alone and keeps its flag.
     *
     * A position is an index into [notes], not into the sequence's steps: one step can be written
     * as several heads, so the two numberings part company as soon as a note crosses a bar line.
     */
    fun beamGroupOf(position: Int): IntRange? = beamGroups.firstOrNull { position in it }
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
     * @param reserveAccidentals gives every note the room an accidental takes whether it has one or
     *   not. A practice that shows one note after another from a pool wants this: without it a line
     *   would be laid out differently for C than for C sharp, and the note would jump sideways as
     *   the pitch changed. A written-out piece does not want it, and does not get it by default.
     */
    fun of(
        sequence: ToneSequence,
        prefix: StaffPrefix = StaffPrefix.None,
        styles: List<StaffNoteStyle> = emptyList(),
        labelWidthSpaces: Float = 0f,
        reserveAccidentals: Boolean = false,
    ): StaffLayout {
        val styleOf = { index: Int -> styles.getOrElse(index) { StaffNoteStyle.HEAD } }
        val written = writtenNotesOf(sequence, styleOf)

        val notes = mutableListOf<StaffNotePlacement>()
        val barLines = mutableListOf<Float>()
        var cursor = prefix.widthSpaces + LEADING_SPACES

        val beatsPerBar = sequence.timeSignature.beatsPerBar.toDouble()
        var nextBarBeat = beatsPerBar

        written.forEachIndexed { position, piece ->
            val spelling = piece.note?.let { TrebleStaff.placementOf(it, sequence.accidentalStyle) }
                // A rest is registered on the middle line whatever is around it: it says how long
                // the silence is, not how high it is.
                ?: StaffPlacement(REST_LINE_STEP, Accidental.NONE)
            // The far end of a tie is the same note still sounding, so it does not say its
            // accidental again - the first head already did, and repeating it would read as a
            // second alteration.
            val placement = if (piece.continuesPrevious) {
                spelling.copy(accidental = Accidental.NONE)
            } else {
                spelling
            }
            val startBeat = piece.startBeat.value

            if (position > 0 && startBeat >= nextBarBeat - BEAT_TOLERANCE) {
                barLines += cursor
                cursor += BAR_LINE_SPACES
                // A note long enough to swallow a whole bar leaves only one line, not several.
                while (nextBarBeat <= startBeat + BEAT_TOLERANCE) nextBarBeat += beatsPerBar
            }

            // A rest is always drawn. Writing a note as its name is a display choice about a
            // pitch, and a rest has no pitch to have a name.
            val style = if (piece.note == null) StaffNoteStyle.HEAD else styleOf(piece.stepIndex)
            // A written name carries its own accidental in its letters and has no dots to hang off
            // it, so it needs neither of those rooms - only a box of its own to sit in the middle of.
            val accidentalRoom = when {
                style == StaffNoteStyle.NAME -> 0f
                reserveAccidentals -> ACCIDENTAL_SPACES
                placement.accidental == Accidental.NONE -> 0f
                else -> ACCIDENTAL_SPACES
            }

            notes += StaffNotePlacement(
                index = piece.stepIndex,
                headXSpaces = if (style == StaffNoteStyle.NAME) {
                    cursor + labelWidthSpaces / 2f
                } else {
                    cursor + accidentalRoom + HEAD_OFFSET_SPACES
                },
                placement = placement,
                notated = piece.notated,
                style = style,
                tiedToNext = piece.tiedToNext,
                isRest = piece.note == null,
            )

            val dots = piece.notated?.dots ?: 0
            val advance = advanceSpaces(piece.duration.value)
            cursor += if (style == StaffNoteStyle.NAME) {
                // Never narrower than the name itself, and never narrower than the note's length
                // asks for, so a line of names still reads left to right in time.
                maxOf(labelWidthSpaces, advance)
            } else {
                accidentalRoom + advance + dots * DOT_SPACES
            }
        }

        // A row of a page can be a bar of nothing but rests, which asks for no room of its own:
        // the plain staff is exactly what it needs.
        val extent = if (sequence.notes.isEmpty()) {
            0..TOP_LINE_STEP
        } else {
            staffStepRange(sequence.notes, sequence.accidentalStyle)
        }
        // A written name is taller than the head it stands in for, and a clef reaches past both
        // outer lines. Either one clipped is the easiest way to get this wrong, so both are asked.
        val nameRoom = if (notes.any { it.style == StaffNoteStyle.NAME }) LABEL_HEADROOM_STEPS else 0
        // Beams reach past the end of a stem, so the drawing needs a little more room than the
        // notes strictly ask for; padding the range keeps every note the same size whatever is
        // beamed.
        val stepRange = ((extent.first - BEAM_HEADROOM_STEPS - nameRoom)..
            (extent.last + BEAM_HEADROOM_STEPS + nameRoom)).including(prefix.stepRange)

        return StaffLayout(
            notes = notes,
            barLineXSpaces = barLines,
            prefixWidthSpaces = prefix.widthSpaces,
            widthSpaces = cursor + TRAILING_SPACES,
            stepRange = stepRange,
            beamGroups = beamGroupsOf(sequence, written, styleOf),
        )
    }

    /**
     * The notes as they are written, with a name kept whole.
     *
     * Splitting a held note at a bar line is notation, and a note shown as its name is out of
     * notation - it has no written length to divide. So a name stays one label taking the room its
     * whole step asks for, and only drawn notes are cut. Where a name does span a bar line, the
     * line lands after it rather than inside it, which is the same simplification this engine
     * already makes for a note that swallows a whole bar.
     */
    private fun writtenNotesOf(
        sequence: ToneSequence,
        styleOf: (Int) -> StaffNoteStyle,
    ): List<NotatedNote> {
        return sequence.notatedReading().mapNotNull { piece ->
            when {
                piece.note == null -> piece
                styleOf(piece.stepIndex) == StaffNoteStyle.HEAD -> piece
                piece.continuesPrevious -> null
                else -> piece.copy(
                    duration = sequence.steps[piece.stepIndex].duration,
                    tiedToNext = false,
                )
            }
        }
    }

    /**
     * The beams, over positions on the page, with the written names taken out of them.
     *
     * [ToneSequence.beamGroups] counts in steps, and a step can be written as more than one note, so
     * the groups are moved onto the page's own numbering first. Only a note shorter than a beat is
     * ever beamed and nothing that short crosses a bar line, so each beamed step is exactly one
     * written note and a group stays the run of positions it was.
     *
     * A name has no stem for a beam to reach, so it does not merely go undrawn - it breaks the group
     * it was in. What is left either side of it is beamed if two notes are still standing there,
     * and flagged if only one is. Done here rather than in the drawing so that the answer can be
     * checked without a canvas.
     */
    private fun beamGroupsOf(
        sequence: ToneSequence,
        written: List<NotatedNote>,
        styleOf: (Int) -> StaffNoteStyle,
    ): List<IntRange> {
        val positionOfStep = HashMap<Int, Int>()
        written.forEachIndexed { position, piece ->
            if (!piece.continuesPrevious) positionOfStep.getOrPut(piece.stepIndex) { position }
        }
        val drawnAt = { position: Int ->
            written[position].note != null &&
                styleOf(written[position].stepIndex) == StaffNoteStyle.HEAD
        }

        return sequence.beamGroups().mapNotNull { steps ->
            val first = positionOfStep[steps.first] ?: return@mapNotNull null
            val last = positionOfStep[steps.last] ?: return@mapNotNull null
            first..last
        }.flatMap { group ->
            if (group.all(drawnAt)) return@flatMap listOf(group)

            val runs = mutableListOf<IntRange>()
            var start: Int? = null
            // One past the end, so a run that reaches the end of the group is closed like any other.
            for (position in group.first..group.last + 1) {
                val drawn = position <= group.last && drawnAt(position)
                val open = start
                when {
                    drawn && open == null -> start = position
                    !drawn && open != null -> {
                        if (position - 1 > open) runs += open..position - 1
                        start = null
                    }
                }
            }
            runs
        }
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

    /** The middle line, which is what every rest but the whole rest is registered on. */
    private const val REST_LINE_STEP = 4

    /** A name written on the line it belongs to overhangs it; one step either side covers that. */
    private const val LABEL_HEADROOM_STEPS = 1

    /** Beats are doubles; a boundary should not be missed because of the last bit of one. */
    private const val BEAT_TOLERANCE = 1e-9
}

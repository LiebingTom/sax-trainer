package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.StaffPlacement
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.beamGroups
import com.example.saxtrainer.core.music.notatedDurationOf
import kotlin.math.pow

/** One note, placed. Positions are in staff spaces, so the layout is independent of any screen. */
data class StaffNotePlacement(
    val index: Int,
    /** Centre of the note head, in staff spaces from the left edge. */
    val headXSpaces: Float,
    val placement: StaffPlacement,
    /** How the note is written, or null when no single head says it - a five-beat tone, say. */
    val notated: NotatedDuration?,
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

    fun of(sequence: ToneSequence): StaffLayout {
        val placements = sequence.steps.map {
            TrebleStaff.placementOf(it.note, sequence.accidentalStyle)
        }

        val notes = mutableListOf<StaffNotePlacement>()
        val barLines = mutableListOf<Float>()
        var cursor = LEADING_SPACES

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

            val accidentalRoom =
                if (placement.accidental == com.example.saxtrainer.core.music.Accidental.NONE) {
                    0f
                } else {
                    ACCIDENTAL_SPACES
                }

            notes += StaffNotePlacement(
                index = index,
                headXSpaces = cursor + accidentalRoom + HEAD_OFFSET_SPACES,
                placement = placement,
                notated = notated,
            )

            val dots = notated?.dots ?: 0
            cursor += accidentalRoom + advanceSpaces(sequence.steps[index].duration.value) +
                dots * DOT_SPACES
        }

        // Beams reach past the end of a stem, so the drawing needs a little more room than the notes
        // strictly ask for; padding the range keeps every note the same size whatever is beamed.
        val extent = staffStepRange(sequence.notes, sequence.accidentalStyle)

        return StaffLayout(
            notes = notes,
            barLineXSpaces = barLines,
            widthSpaces = cursor + TRAILING_SPACES,
            stepRange = (extent.first - BEAM_HEADROOM_STEPS)..(extent.last + BEAM_HEADROOM_STEPS),
            beamGroups = sequence.beamGroups(),
        )
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

    /** Beats are doubles; a boundary should not be missed because of the last bit of one. */
    private const val BEAT_TOLERANCE = 1e-9
}

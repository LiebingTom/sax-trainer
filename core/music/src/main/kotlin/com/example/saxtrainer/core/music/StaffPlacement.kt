package com.example.saxtrainer.core.music

/** Accidental drawn in front of a note head. */
enum class Accidental {
    NONE,
    SHARP,
    FLAT,
    NATURAL,
}

/** How an altered pitch is spelled: C sharp or D flat name the same sounding note. */
enum class AccidentalStyle {
    SHARPS,
    FLATS,
}

/**
 * Where a note sits on a staff, expressed in diatonic steps rather than pixels, so the geometry
 * stays a rendering concern.
 *
 * [stepsFromBottomLine] counts staff positions from the bottom line of the treble staff (E4 = 0):
 * one step is half a staff space, so even values are lines and odd values are spaces. Values below
 * zero and above eight are outside the staff and need ledger lines.
 */
data class StaffPlacement(
    val stepsFromBottomLine: Int,
    val accidental: Accidental,
) {
    /** Steps at which a ledger line must be drawn below the staff, nearest to the staff first. */
    val ledgerLinesBelow: List<Int>
        get() = if (stepsFromBottomLine > BELOW_FIRST_LEDGER) {
            emptyList()
        } else {
            (BELOW_FIRST_LEDGER downTo stepsFromBottomLine step 2).toList()
        }

    /** Steps at which a ledger line must be drawn above the staff, nearest to the staff first. */
    val ledgerLinesAbove: List<Int>
        get() = if (stepsFromBottomLine < ABOVE_FIRST_LEDGER) {
            emptyList()
        } else {
            (ABOVE_FIRST_LEDGER..stepsFromBottomLine step 2).toList()
        }

    /** True while the note sits between the outer staff lines. */
    val isOnStaff: Boolean
        get() = stepsFromBottomLine in 0..TOP_LINE

    /** Notes below the middle line point their stem up, the rest point it down. */
    val stemUp: Boolean
        get() = stepsFromBottomLine < MIDDLE_LINE

    private companion object {
        const val TOP_LINE = 8
        const val MIDDLE_LINE = 4
        const val BELOW_FIRST_LEDGER = -2
        const val ABOVE_FIRST_LEDGER = 10
    }
}

/**
 * Maps notes onto the treble staff. Only the treble clef is supported for now, which is what the
 * app needs for written saxophone parts.
 */
object TrebleStaff {

    /** Diatonic index of E4, the bottom line of the treble staff. */
    private const val BOTTOM_LINE_INDEX = 4 * STEPS_PER_OCTAVE + 2

    /**
     * Staff position of [note].
     *
     * [style] decides how altered pitches are spelled, which changes both the accidental and the
     * line the head sits on: C sharp shares its line with C, D flat shares its line with D.
     * With [naturalsExplicit] the unaltered pitches carry a natural sign instead of nothing.
     */
    fun placementOf(
        note: Note,
        style: AccidentalStyle = AccidentalStyle.SHARPS,
        naturalsExplicit: Boolean = false,
    ): StaffPlacement {
        val altered = note.name.isAltered
        val letterIndex = when {
            !altered -> note.name.naturalLetterIndex
            style == AccidentalStyle.SHARPS -> note.name.naturalLetterIndex
            // Spelled downwards from the next letter, e.g. C sharp becomes D flat.
            else -> (note.name.naturalLetterIndex + 1) % LETTERS_PER_OCTAVE
        }
        // A flat spelling of A sharp is B flat, which stays in the same octave; only a flat spelling
        // that wraps past B would change octave, and no such pitch exists in this model.
        val octave = note.octave

        val diatonicIndex = octave * LETTERS_PER_OCTAVE + letterIndex
        val accidental = when {
            !altered && naturalsExplicit -> Accidental.NATURAL
            !altered -> Accidental.NONE
            style == AccidentalStyle.SHARPS -> Accidental.SHARP
            else -> Accidental.FLAT
        }

        return StaffPlacement(
            stepsFromBottomLine = diatonicIndex - BOTTOM_LINE_INDEX,
            accidental = accidental,
        )
    }

    private const val LETTERS_PER_OCTAVE = 7
}

private const val STEPS_PER_OCTAVE = 7

/** True for the pitches this model spells with a sharp. */
private val NoteName.isAltered: Boolean
    get() = when (this) {
        NoteName.C_SHARP, NoteName.D_SHARP, NoteName.F_SHARP,
        NoteName.G_SHARP, NoteName.A_SHARP,
        -> true

        else -> false
    }

/** Position of the note's letter within an octave, C = 0 through B = 6. */
private val NoteName.naturalLetterIndex: Int
    get() = when (this) {
        NoteName.C, NoteName.C_SHARP -> 0
        NoteName.D, NoteName.D_SHARP -> 1
        NoteName.E -> 2
        NoteName.F, NoteName.F_SHARP -> 3
        NoteName.G, NoteName.G_SHARP -> 4
        NoteName.A, NoteName.A_SHARP -> 5
        NoteName.B -> 6
    }

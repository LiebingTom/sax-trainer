package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.fingering.SaxKey.BIS
import com.example.saxtrainer.core.fingering.SaxKey.FRONT_F
import com.example.saxtrainer.core.fingering.SaxKey.HIGH_F_SHARP
import com.example.saxtrainer.core.fingering.SaxKey.LH1
import com.example.saxtrainer.core.fingering.SaxKey.LH2
import com.example.saxtrainer.core.fingering.SaxKey.LH3
import com.example.saxtrainer.core.fingering.SaxKey.LH_GS
import com.example.saxtrainer.core.fingering.SaxKey.LH_LOW_B
import com.example.saxtrainer.core.fingering.SaxKey.LH_LOW_BB
import com.example.saxtrainer.core.fingering.SaxKey.LH_LOW_CS
import com.example.saxtrainer.core.fingering.SaxKey.RH_C
import com.example.saxtrainer.core.fingering.SaxKey.OCTAVE
import com.example.saxtrainer.core.fingering.SaxKey.PALM_D
import com.example.saxtrainer.core.fingering.SaxKey.PALM_EB
import com.example.saxtrainer.core.fingering.SaxKey.PALM_F
import com.example.saxtrainer.core.fingering.SaxKey.RH1
import com.example.saxtrainer.core.fingering.SaxKey.RH2
import com.example.saxtrainer.core.fingering.SaxKey.RH3
import com.example.saxtrainer.core.fingering.SaxKey.RH_EB
import com.example.saxtrainer.core.fingering.SaxKey.SIDE_BB
import com.example.saxtrainer.core.fingering.SaxKey.SIDE_E
import com.example.saxtrainer.core.fingering.SaxKey.SIDE_F_SHARP
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Tenor saxophone fingerings, written B flat 3 up to F sharp 6.
 *
 * The standard tenor saxophone grips, alternates included, written out key by key and treated as
 * the authoritative source rather than derived from any rule. Notes are written pitch, so the same
 * grips serve the rest of the saxophone family; only what sounds differs.
 *
 * Beginner note sets are deliberately not defined here - which notes a first exercise uses is a
 * question for a trainer, not for the instrument.
 */
object TenorSaxophoneFingerings : FingeringChart {

    private val entries: List<NoteFingerings> = listOf(
        note(NoteName.A_SHARP, 3) {
            standard(LH1, LH2, LH3, LH_LOW_BB, RH1, RH2, RH3, RH_C)
        },
        note(NoteName.B, 3) {
            standard(LH1, LH2, LH3, LH_LOW_B, RH1, RH2, RH3, RH_C)
        },
        note(NoteName.C, 4) {
            standard(LH1, LH2, LH3, RH1, RH2, RH3, RH_C)
        },
        note(NoteName.C_SHARP, 4) {
            standard(LH1, LH2, LH3, LH_LOW_CS, RH1, RH2, RH3, RH_C)
        },
        note(NoteName.D, 4) {
            standard(LH1, LH2, LH3, RH1, RH2, RH3)
        },
        note(NoteName.D_SHARP, 4) {
            standard(LH1, LH2, LH3, RH1, RH2, RH3, RH_EB)
        },
        note(NoteName.E, 4) {
            standard(LH1, LH2, LH3, RH1, RH2)
        },
        note(NoteName.F, 4) {
            standard(LH1, LH2, LH3, RH1)
        },
        note(NoteName.F_SHARP, 4) {
            standard(LH1, LH2, LH3, RH2)
            alternate(LH1, LH2, LH3, RH1, SIDE_F_SHARP)
        },
        note(NoteName.G, 4) {
            standard(LH1, LH2, LH3)
        },
        note(NoteName.G_SHARP, 4) {
            standard(LH1, LH2, LH3, LH_GS)
        },
        note(NoteName.A, 4) {
            standard(LH1, LH2)
        },
        note(NoteName.A_SHARP, 4) {
            standard(LH1, BIS)
            alternate(LH1, LH2, SIDE_BB)
            alternate(LH1, RH1)
        },
        note(NoteName.B, 4) {
            standard(LH1)
        },
        note(NoteName.C, 5) {
            standard(LH2)
        },
        note(NoteName.C_SHARP, 5) {
            standard()
            alternate(LH3)
        },
        note(NoteName.D, 5) {
            standard(OCTAVE, LH1, LH2, LH3, RH1, RH2, RH3)
        },
        note(NoteName.D_SHARP, 5) {
            standard(OCTAVE, LH1, LH2, LH3, RH1, RH2, RH3, RH_EB)
        },
        note(NoteName.E, 5) {
            standard(OCTAVE, LH1, LH2, LH3, RH1, RH2)
        },
        note(NoteName.F, 5) {
            standard(OCTAVE, LH1, LH2, LH3, RH1)
        },
        note(NoteName.F_SHARP, 5) {
            standard(OCTAVE, LH1, LH2, LH3, RH2)
            alternate(OCTAVE, LH1, LH2, LH3, RH1, SIDE_F_SHARP)
        },
        note(NoteName.G, 5) {
            standard(OCTAVE, LH1, LH2, LH3)
        },
        note(NoteName.G_SHARP, 5) {
            standard(OCTAVE, LH1, LH2, LH3, LH_GS)
        },
        note(NoteName.A, 5) {
            standard(OCTAVE, LH1, LH2)
        },
        note(NoteName.A_SHARP, 5) {
            standard(OCTAVE, LH1, BIS)
            alternate(OCTAVE, LH1, LH2, SIDE_BB)
            alternate(OCTAVE, LH1, RH1)
        },
        note(NoteName.B, 5) {
            standard(OCTAVE, LH1)
        },
        note(NoteName.C, 6) {
            standard(OCTAVE, LH2)
        },
        note(NoteName.C_SHARP, 6) {
            standard(OCTAVE)
        },
        note(NoteName.D, 6) {
            standard(OCTAVE, PALM_D)
        },
        note(NoteName.D_SHARP, 6) {
            standard(OCTAVE, PALM_D, PALM_EB)
        },
        note(NoteName.E, 6) {
            standard(OCTAVE, PALM_D, PALM_EB, PALM_F)
            alternate(OCTAVE, FRONT_F, LH2, LH3, RH1, RH2)
        },
        note(NoteName.F, 6) {
            standard(OCTAVE, PALM_D, PALM_EB, PALM_F, SIDE_E)
            alternate(OCTAVE, FRONT_F, LH2)
        },
        note(NoteName.F_SHARP, 6) {
            standard(OCTAVE, PALM_D, PALM_EB, PALM_F, SIDE_E, HIGH_F_SHARP)
            alternate(OCTAVE, FRONT_F, LH2, RH1, SIDE_BB)
        },
    )

    private val byNote: Map<Note, NoteFingerings> = entries.associateBy { it.note }

    override val supportedNotes: List<Note> = entries.map { it.note }

    override fun fingeringsFor(writtenNote: Note): NoteFingerings? = byNote[writtenNote]

    /** Reads as the chart does: one standard line, then any alternates. */
    private fun note(
        name: NoteName,
        octave: Int,
        build: Builder.() -> Unit,
    ): NoteFingerings = Builder(Note(name, octave)).apply(build).build()

    private class Builder(private val note: Note) {
        private var standard: Fingering? = null
        private val alternates = mutableListOf<Fingering>()

        fun standard(vararg keys: SaxKey) {
            standard = Fingering(keys.toSet(), FingeringKind.STANDARD)
        }

        fun alternate(vararg keys: SaxKey) {
            alternates += Fingering(keys.toSet(), FingeringKind.ALTERNATE)
        }

        fun build(): NoteFingerings = NoteFingerings(
            note = note,
            standard = requireNotNull(standard) { "${note.label} has no standard fingering" },
            alternates = alternates.toList(),
        )
    }
}

/**
 * The chart the app uses today.
 *
 * Kept as a name of its own so callers do not have to care which instrument's chart is in force;
 * it is the tenor data, which is written-pitch and therefore shared across the saxophone family.
 */
object SaxophoneFingerings : FingeringChart by TenorSaxophoneFingerings

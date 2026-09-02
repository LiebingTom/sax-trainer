package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Standard saxophone fingerings for the first octave a beginner learns: written C4 up to C5, plus
 * the two accidentals that turn up immediately, B flat and F sharp.
 *
 * Written notes, so the same chart serves tenor, alto and the rest of the family. Every note here is
 * below the octave register, which is why no fingering presses [SaxKey.OCTAVE].
 */
object SaxophoneFingerings : FingeringChart {

    private val fingerings: Map<Note, Fingering> = linkedMapOf(
        // Low C: the whole hand plus the left little finger.
        note(NoteName.C, 4) to fingering(
            SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
            SaxKey.RH1, SaxKey.RH2, SaxKey.RH3,
            SaxKey.LOW_C,
        ),
        // D, E, F: fingers lift one at a time from the bottom.
        note(NoteName.D, 4) to fingering(
            SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
            SaxKey.RH1, SaxKey.RH2, SaxKey.RH3,
        ),
        note(NoteName.E, 4) to fingering(
            SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
            SaxKey.RH1, SaxKey.RH2,
        ),
        note(NoteName.F, 4) to fingering(
            SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
            SaxKey.RH1,
        ),
        // F sharp: the right middle finger alone under the left hand.
        note(NoteName.F_SHARP, 4) to fingering(
            SaxKey.LH1, SaxKey.LH2, SaxKey.LH3,
            SaxKey.RH2,
        ),
        note(NoteName.G, 4) to fingering(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3),
        note(NoteName.A, 4) to fingering(SaxKey.LH1, SaxKey.LH2),
        // B flat with the side key, the fingering a beginner is taught first.
        note(NoteName.A_SHARP, 4) to fingering(SaxKey.LH1, SaxKey.SIDE_BB),
        note(NoteName.B, 4) to fingering(SaxKey.LH1),
        // C: the second finger on its own.
        note(NoteName.C, 5) to fingering(SaxKey.LH2),
    )

    override val supportedNotes: List<Note> = fingerings.keys.sortedBy { it.midiNumber }

    override fun fingeringFor(writtenNote: Note): Fingering? = fingerings[writtenNote]

    private fun note(name: NoteName, octave: Int) = Note(name, octave)

    private fun fingering(vararg keys: SaxKey) = Fingering(keys.toSet())
}

package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * The notes each unit works on.
 *
 * Written cumulatively, so a later unit always contains everything an earlier one taught and the
 * ladder cannot quietly develop a gap. A test holds this to it.
 */

internal fun note(name: NoteName, octave: Int) = Note(name, octave)

/** C to G: the first five notes, and the whole of the first unit. */
internal val FIRST_NOTES = listOf(
    note(NoteName.C, 4),
    note(NoteName.D, 4),
    note(NoteName.E, 4),
    note(NoteName.F, 4),
    note(NoteName.G, 4),
)

/** The first three, for a lesson that only wants somewhere to begin. */
internal val FIRST_THREE = FIRST_NOTES.take(3)

/** The whole first octave. */
internal val OCTAVE_NOTES = FIRST_NOTES + listOf(
    note(NoteName.A, 4),
    note(NoteName.B, 4),
    note(NoteName.C, 5),
)

/** The octave plus the two accidentals a beginner meets first: B flat and F sharp. */
internal val ACCIDENTAL_NOTES = (
    OCTAVE_NOTES + listOf(note(NoteName.F_SHARP, 4), note(NoteName.A_SHARP, 4))
    ).sortedBy { it.midiNumber }

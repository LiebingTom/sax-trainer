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


/** The seven letter names, so a set can be read as "the plain notes" without listing them twice. */
private val NATURALS = setOf(
    NoteName.C,
    NoteName.D,
    NoteName.E,
    NoteName.F,
    NoteName.G,
    NoteName.A,
    NoteName.B,
)

/**
 * The second register: every grip already learned, played an octave higher with the octave key.
 *
 * Written D5 up to C6. C sharp 5 is deliberately not here - it is the one note in between that is
 * played without the octave key, so it belongs to whichever unit teaches the notes around it rather
 * than to the one whose whole subject is the octave key.
 */
internal val OCTAVE_KEY_NOTES = listOf(
    note(NoteName.D, 5),
    note(NoteName.E, 5),
    note(NoteName.F, 5),
    note(NoteName.F_SHARP, 5),
    note(NoteName.G, 5),
    note(NoteName.A, 5),
    note(NoteName.A_SHARP, 5),
    note(NoteName.B, 5),
    note(NoteName.C, 6),
).sortedBy { it.midiNumber }

/** Without the two accidentals: the plain notes of the second register. */
internal val OCTAVE_KEY_NATURALS = OCTAVE_KEY_NOTES.filter { it.name in NATURALS }

/** The first four, which is enough to feel what the octave key does. */
internal val OCTAVE_KEY_FIRST = OCTAVE_KEY_NATURALS.take(4)

/** Everything taught so far, both octaves of it. */
internal val UPPER_OCTAVE_NOTES = (ACCIDENTAL_NOTES + OCTAVE_KEY_NOTES).sortedBy { it.midiNumber }

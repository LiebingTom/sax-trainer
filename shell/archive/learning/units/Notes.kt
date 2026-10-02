package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote

/**
 * The notes each unit works on.
 *
 * Written cumulatively, so a later unit always contains everything an earlier one taught and the
 * ladder cannot quietly develop a gap. A test holds this to it.
 *
 * Every note here is a written note: what a lesson practises is reading it, so how it is spelled is
 * part of the content. A plain letter is written as it usually is; a note with a sign is given in
 * the spelling the lesson teaches, the way a page of music would print it.
 */

/** A note with no sharp or flat to decide, written the way it usually is. */
internal fun note(name: NoteName, octave: Int): SpelledNote = SpelledNote.of(Note(name, octave))

/** A note exactly as written - "Bb4" is a B flat, never the A sharp it sounds the same as. */
internal fun written(token: String): SpelledNote =
    requireNotNull(SpelledNote.parse(token)) { "$token is not a written note" }

/** C to G: the first five notes, and the whole of the first unit. */
internal val FIRST_NOTES = listOf(
    note(NoteName.C, 4),
    note(NoteName.D, 4),
    note(NoteName.E, 4),
    note(NoteName.F, 4),
    note(NoteName.G, 4),
)

/** The whole first octave. */
internal val OCTAVE_NOTES = FIRST_NOTES + listOf(
    note(NoteName.A, 4),
    note(NoteName.B, 4),
    note(NoteName.C, 5),
)

/** The octave plus the two accidentals a beginner meets first: B flat and F sharp. */
internal val ACCIDENTAL_NOTES = (
    OCTAVE_NOTES + listOf(written("F#4"), written("Bb4"))
    ).sortedWith(SpelledNote.PITCH_ORDER)


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
    written("F#5"),
    note(NoteName.G, 5),
    note(NoteName.A, 5),
    written("Bb5"),
    note(NoteName.B, 5),
    note(NoteName.C, 6),
).sortedWith(SpelledNote.PITCH_ORDER)

// --- keys -----------------------------------------------------------------------------------------
//
// A scale is not another rung of the ladder above: it is the same notes looked at as a key, in the
// order the key puts them in. So these are written as the scales they are rather than derived from
// the sets above, and a unit built on one of them teaches a key rather than a new set of grips.

/** C major, the octave that is already the whole of [OCTAVE_NOTES]. */
internal val C_MAJOR_LOWER = OCTAVE_NOTES

/** The same scale an octave up, which is the same eight grips with the octave key added. */
internal val C_MAJOR_UPPER = listOf(
    note(NoteName.C, 5),
    note(NoteName.D, 5),
    note(NoteName.E, 5),
    note(NoteName.F, 5),
    note(NoteName.G, 5),
    note(NoteName.A, 5),
    note(NoteName.B, 5),
    note(NoteName.C, 6),
)

/** Both octaves of C major, C4 up to C6, with the C in the middle counted once. */
internal val C_MAJOR_BOTH = (C_MAJOR_LOWER + C_MAJOR_UPPER).distinct().sortedWith(SpelledNote.PITCH_ORDER)

/**
 * G major, one octave, G4 up to G5.
 *
 * Only one: the next octave would end on G6, and the chart stops at F sharp 6. The one octave is
 * enough to be the point of the key anyway - it starts in the first register, crosses into the
 * second half way up, and carries the F sharp that makes it G major rather than C.
 */
internal val G_MAJOR = listOf(
    note(NoteName.G, 4),
    note(NoteName.A, 4),
    note(NoteName.B, 4),
    note(NoteName.C, 5),
    note(NoteName.D, 5),
    note(NoteName.E, 5),
    written("F#5"),
    note(NoteName.G, 5),
)

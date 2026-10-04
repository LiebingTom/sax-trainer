package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote

/**
 * The written notes each group works on.
 *
 * Every one is a written note: a lesson practises reading it, so how it is spelled is part of the
 * content. Written C4 to C6 is two octaves; the chromatic groups add D6, but not the C sharp below
 * it. Each group contains
 * every pitch of the one before it - a test holds them to that.
 */

private val LOW_C = Note(NoteName.C, 4)
private val TOP_C = Note(NoteName.C, 6)
private val TOP_D = Note(NoteName.D, 6)

/** A note exactly as written - "F#4" is an F sharp, never the G flat it sounds the same as. */
internal fun written(token: String): SpelledNote =
    requireNotNull(SpelledNote.parse(token)) { "$token is not a written note" }

/** C major over two octaves: C4 up to C6, no sharps or flats. */
internal val C_MAJOR_TWO_OCTAVES: List<SpelledNote> = Note.range(LOW_C, TOP_C)
    .filterNot { it.name.isAccidental }
    .map { SpelledNote.of(it) }

/**
 * The same two octaves plus F sharp and C sharp - everything D major needs inside them. The D at
 * the top of D major's second octave, D6, is left out: the range stays C4 to C6.
 */
internal val D_MAJOR_NOTES: List<SpelledNote> =
    (C_MAJOR_TWO_OCTAVES + listOf("C#4", "F#4", "C#5", "F#5").map(::written))
        .sortedWith(SpelledNote.PITCH_ORDER)

/**
 * Every pitch from C4 up to C6, and the D above it: the chromatic groups' range. The C sharp between
 * C6 and D6 is left out.
 */
private val CHROMATIC_RANGE: List<Note> = Note.range(LOW_C, TOP_C) + TOP_D

/** Every pitch of the chromatic range, the black keys written as sharps. */
internal val CHROMATIC_SHARPS: List<SpelledNote> = CHROMATIC_RANGE
    .map { SpelledNote.of(it, AccidentalStyle.SHARPS) }

/**
 * The same pitches, every black key written both ways - C sharp and D flat, D sharp and E flat - as
 * two notes to read. White keys are written plain, never as E sharp or C flat.
 */
internal val CHROMATIC_SHARPS_AND_FLATS: List<SpelledNote> = bothSpellings(CHROMATIC_RANGE)

/**
 * Every half step from C4 to C6 and no further, the black keys written both ways: what the blues
 * keys after A need, whose blue notes are D flat and A flat as often as C sharp and G sharp.
 */
internal val HALF_STEPS_TWO_OCTAVES: List<SpelledNote> = bothSpellings(Note.range(LOW_C, TOP_C))

/** [pitches] with every black key written as a sharp and as a flat, white keys plain, low to high. */
private fun bothSpellings(pitches: List<Note>): List<SpelledNote> = pitches
    .flatMap { pitch ->
        listOf(SpelledNote.of(pitch, AccidentalStyle.SHARPS), SpelledNote.of(pitch, AccidentalStyle.FLATS)).distinct()
    }
    .sortedWith(SpelledNote.PITCH_ORDER)

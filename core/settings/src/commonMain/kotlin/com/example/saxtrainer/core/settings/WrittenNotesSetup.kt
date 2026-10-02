package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.SpelledNote

/**
 * Writes down a choice of written notes so that both halves of each survive.
 *
 * The pitches go under [pitchKey] as MIDI numbers, exactly as every setup has always stored them;
 * how each is written goes under [writtenKey] as tokens like "Eb4". Two keys rather than one new
 * format, so a setup stays readable by anything that only knows the first - and a pitch chosen twice,
 * as D sharp and as E flat, is one number but two tokens.
 */
fun ExerciseSetupBuilder.putWrittenNotes(
    pitchKey: String,
    writtenKey: String,
    notes: Collection<SpelledNote>,
) {
    putInts(pitchKey, notes.map { it.note.midiNumber }.distinct().sorted())
    putStrings(writtenKey, notes.sortedWith(SpelledNote.PITCH_ORDER).map { it.token })
}

/**
 * The written notes [putWrittenNotes] stored, or null when none were.
 *
 * A setup from before spellings were kept has only the pitches, and each is then written the way it
 * usually is - the same notes it always practised, shown the way they were always shown. A token
 * this version cannot read is dropped rather than failing the whole setup.
 */
fun ExerciseSetup.writtenNotes(pitchKey: String, writtenKey: String): List<SpelledNote>? =
    strings(writtenKey)?.mapNotNull { SpelledNote.parse(it) }
        ?: ints(pitchKey)?.map { SpelledNote.of(Note.fromMidi(it)) }

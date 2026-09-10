package com.example.saxtrainer.core.music

import kotlin.math.pow
import kotlin.math.roundToInt

/** The twelve pitch classes, named with sharps. */
enum class NoteName(val displayName: String, val semitonesFromC: Int) {
    C("C", 0),
    C_SHARP("C#", 1),
    D("D", 2),
    D_SHARP("D#", 3),
    E("E", 4),
    F("F", 5),
    F_SHARP("F#", 6),
    G("G", 7),
    G_SHARP("G#", 8),
    A("A", 9),
    A_SHARP("A#", 10),
    B("B", 11),
    ;

    /** True for the five pitch classes that are written with an accidental: the black keys. */
    val isAccidental: Boolean
        get() = when (this) {
            C_SHARP, D_SHARP, F_SHARP, G_SHARP, A_SHARP -> true
            else -> false
        }

    companion object {
        fun ofSemitone(semitonesFromC: Int): NoteName {
            val normalised = ((semitonesFromC % 12) + 12) % 12
            return entries.first { it.semitonesFromC == normalised }
        }
    }
}

/**
 * A pitch in scientific notation (A4 = MIDI 69). Values in the app are always concert pitch;
 * instrument transposition is applied when a note is shown or chosen, never when it is stored.
 */
data class Note(val name: NoteName, val octave: Int) {

    val midiNumber: Int = (octave + 1) * SEMITONES_PER_OCTAVE + name.semitonesFromC

    val label: String get() = "${name.displayName}$octave"

    /** Frequency for the given tuning reference, which is a setting rather than a constant. */
    fun frequencyHz(referenceA4Hz: Double = DEFAULT_A4_HZ): Double =
        referenceA4Hz * 2.0.pow((midiNumber - A4_MIDI) / SEMITONES_PER_OCTAVE.toDouble())

    fun transposedBy(semitones: Int): Note = fromMidi(midiNumber + semitones)

    companion object {
        const val DEFAULT_A4_HZ = 440.0
        const val A4_MIDI = 69
        const val SEMITONES_PER_OCTAVE = 12

        fun fromMidi(midiNumber: Int): Note = Note(
            name = NoteName.ofSemitone(midiNumber % SEMITONES_PER_OCTAVE),
            octave = midiNumber / SEMITONES_PER_OCTAVE - 1,
        )

        /** Nearest note to a measured frequency; the basis for a future tuner. */
        fun nearestTo(frequencyHz: Double, referenceA4Hz: Double = DEFAULT_A4_HZ): Note {
            require(frequencyHz > 0) { "frequency must be positive" }
            val midi = A4_MIDI + SEMITONES_PER_OCTAVE *
                (kotlin.math.ln(frequencyHz / referenceA4Hz) / kotlin.math.ln(2.0))
            return fromMidi(midi.roundToInt())
        }

        /** Inclusive chromatic range, used to build note pickers. */
        fun range(lowest: Note, highest: Note): List<Note> =
            (lowest.midiNumber..highest.midiNumber).map(::fromMidi)
    }
}

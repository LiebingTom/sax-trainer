package com.example.saxtrainer.core.music

/**
 * A note as it is written: a letter, the sign in front of it, and the octave that letter sits in.
 *
 * The written counterpart of [Note]. A [Note] is a pitch - what sounds, what the microphone hears,
 * what a fingering is looked up by - and each of the twelve is named once. A [SpelledNote] is what a
 * reader sees, and one pitch can be written more than one way: D sharp 4 and E flat 4 are the same
 * [note] but two different things to learn to read, and C flat 4 is the pitch B3 written on the C
 * line. Equality here is written equality; compare [note] to ask whether two of them sound alike.
 *
 * [octave] is the written octave - the one the letter belongs to - so B sharp 3 is written in
 * octave 3 and sounds as C4.
 */
data class SpelledNote(
    /** C = 0 through B = 6, counted in letters rather than semitones. */
    val letterIndex: Int,
    /** NONE, SHARP or FLAT. A natural sign is a way of showing a note, not a different note. */
    val accidental: Accidental,
    val octave: Int,
) {
    init {
        require(letterIndex in 0 until LETTERS_PER_OCTAVE) { "there is no letter $letterIndex" }
        require(accidental != Accidental.NATURAL) {
            "a natural sign is how a note is shown, not which note it is"
        }
    }

    /** The pitch this is a way of writing. */
    val note: Note = Note.fromMidi(
        (octave + 1) * Note.SEMITONES_PER_OCTAVE +
            NATURAL_SEMITONES[letterIndex] + accidental.semitones,
    )

    /** Letters counted up from C0: the axis a staff is laid out along. */
    val diatonicIndex: Int get() = octave * LETTERS_PER_OCTAVE + letterIndex

    /** The letter and sign as [NoteNaming] and the staff read them. */
    val spelling: NoteSpelling get() = NoteSpelling(letterIndex, accidental, octave)

    /** Plain text, as content files and stored setups write a note: "C4", "F#4", "Eb4", "B#3". */
    val token: String
        get() = LETTERS[letterIndex].toString() + when (accidental) {
            Accidental.SHARP -> "#"
            Accidental.FLAT -> "b"
            else -> ""
        } + octave

    override fun toString(): String = token

    companion object {

        /** [note] written the way [style] spells it; by default the way it usually is. */
        fun of(note: Note, style: AccidentalStyle = note.conventionalStyle()): SpelledNote =
            TrebleStaff.spellingOf(note, style).let {
                SpelledNote(it.letterIndex, it.accidental, it.octave)
            }

        /**
         * Every way of writing [note] with at most one sharp or flat, the usual one first.
         *
         * Two for most pitches - C sharp and D flat, E and F flat, B and C flat - and one for D, G
         * and A, which only a double sharp or flat could spell differently.
         */
        fun spellingsOf(note: Note): List<SpelledNote> {
            val usual = of(note)
            val others = (usual.diatonicIndex - 1..usual.diatonicIndex + 1)
                .mapNotNull { onLetter(it, note) }
                .filter { it != usual }
            return listOf(usual) + others
        }

        /**
         * [pitch] written on the letter [diatonicIndex] (counted as [SpelledNote.diatonicIndex]
         * is), or null when that would take more than one sharp or flat.
         */
        fun onLetter(diatonicIndex: Int, pitch: Note): SpelledNote? {
            val letterIndex = diatonicIndex.mod(LETTERS_PER_OCTAVE)
            val octave = diatonicIndex.floorDiv(LETTERS_PER_OCTAVE)
            val natural = (octave + 1) * Note.SEMITONES_PER_OCTAVE + NATURAL_SEMITONES[letterIndex]
            val accidental = when (pitch.midiNumber - natural) {
                0 -> Accidental.NONE
                1 -> Accidental.SHARP
                -1 -> Accidental.FLAT
                else -> return null
            }
            return SpelledNote(letterIndex, accidental, octave)
        }

        /** Reads a [token] - "C4", "F#4", "Bb3", also with ♯ and ♭. Null if it is not one. */
        fun parse(token: String): SpelledNote? {
            val match = TOKEN.matchEntire(token.trim()) ?: return null
            val (letter, sign, octaveText) = match.destructured
            val letterIndex = LETTERS.indexOf(letter.uppercase().first())
            val octave = octaveText.toIntOrNull() ?: return null
            val accidental = when (sign) {
                "#", "♯" -> Accidental.SHARP
                "b", "♭" -> Accidental.FLAT
                else -> Accidental.NONE
            }
            return SpelledNote(letterIndex, accidental, octave)
        }

        /**
         * Lowest pitch first, and for one pitch the lower letter first - D sharp before E flat - so
         * a list of written notes reads up the staff.
         */
        val PITCH_ORDER: Comparator<SpelledNote> =
            compareBy<SpelledNote>({ it.note.midiNumber }, { it.diatonicIndex })

        private const val LETTERS = "CDEFGAB"
        private const val LETTERS_PER_OCTAVE = 7
        private val NATURAL_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val TOKEN = Regex("^([A-Ga-g])([#b♯♭]?)(-?\\d+)$")
    }
}

private val Accidental.semitones: Int
    get() = when (this) {
        Accidental.SHARP -> 1
        Accidental.FLAT -> -1
        Accidental.NONE, Accidental.NATURAL -> 0
    }

/**
 * How a pitch that was heard, rather than read, is written.
 *
 * The first of [written] that sounds as this pitch, so a caller lists what it would rather show
 * first - the note being asked for, then the rest of the exercise. A pitch none of them sounds as is
 * spelled as [fallback] says. Only the spelling is borrowed: the pitch is always this one.
 */
fun Note.spelledAs(
    written: Iterable<SpelledNote>,
    fallback: AccidentalStyle = conventionalStyle(),
): SpelledNote = written.firstOrNull { it.note == this } ?: SpelledNote.of(this, fallback)

/** Each pitch written the way it usually is - for material that has no spelling of its own. */
fun List<Note>.spelledConventionally(): List<SpelledNote> = map { SpelledNote.of(it) }

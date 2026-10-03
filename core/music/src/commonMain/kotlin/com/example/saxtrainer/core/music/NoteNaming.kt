package com.example.saxtrainer.core.music

/**
 * How a note is spelled: which letter it sits on and what sign, if any, stands in front of it.
 *
 * The same twelve pitches can be written more than one way, and the choice belongs to the music
 * rather than to the pitch. [TrebleStaff.spellingOf] works it out once; the staff draws from it and
 * [NoteNaming] reads from it, so a note's name and its position can never disagree.
 */
data class NoteSpelling(
    /** C = 0 through B = 6, counted in letters rather than semitones. */
    val letterIndex: Int,
    val accidental: Accidental,
    val octave: Int,
)

/**
 * What the note letters are called.
 *
 * A display convention only: nothing about pitch, fingering or measurement changes with it. The two
 * systems disagree on exactly one letter - what English calls B, German calls H, and what German
 * calls B is a B flat - which is why the setting exists at all.
 */
enum class NoteNaming(val displayName: String, val description: String) {
    INTERNATIONAL(
        "International",
        "C  C♯  D  D♯  E  F  F♯  G  G♯  A  B♭  B",
    ),
    GERMAN(
        "German",
        "C  Cis  D  Dis  E  F  Fis  G  Gis  A  B  H",
    ),
    ;

    /**
     * The name of a note without its octave, spelled as [style] asks.
     *
     * The default is the spelling a musician reaches for first - see [Note.conventionalStyle] - so
     * a note met on its own comes out as B flat rather than A sharp. Music that has decided how it
     * is written, such as a scale in a flat key, passes its own style instead.
     */
    fun nameOf(note: Note, style: AccidentalStyle = note.conventionalStyle()): String =
        nameOf(TrebleStaff.spellingOf(note, style))

    /** The name with its octave, as in "B♭4" or "B4", the octave numbered as [octaves] asks. */
    fun labelOf(
        note: Note,
        style: AccidentalStyle = note.conventionalStyle(),
        octaves: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    ): String = nameOf(note, style) + octaves.numberOf(note.octave)

    /** The name of a written note, exactly as written: E flat stays E flat, B sharp stays B sharp. */
    fun nameOf(written: SpelledNote): String = nameOf(written.spelling)

    /** The same with its written octave, as in "C♭4" - the octave of the letter, not of the pitch. */
    fun labelOf(written: SpelledNote, octaves: OctaveNumbering = OctaveNumbering.SCIENTIFIC): String =
        nameOf(written) + octaves.numberOf(written.octave)

    private fun nameOf(spelling: NoteSpelling): String {
        val letter = LETTERS[spelling.letterIndex]
        return when (this) {
            INTERNATIONAL -> when (spelling.accidental) {
                Accidental.NONE -> "$letter"
                Accidental.SHARP -> "$letter♯"
                Accidental.FLAT -> "$letter♭"
                Accidental.NATURAL -> "$letter♮"
            }

            // German renames B to H, keeps B for the B flat, and spells the rest with the endings
            // -is and -es, elided where the language elides them: Es and As, not Ees and Aes.
            GERMAN -> when (spelling.accidental) {
                Accidental.NONE -> germanLetter(spelling.letterIndex)
                Accidental.NATURAL -> germanLetter(spelling.letterIndex) + "♮"
                Accidental.SHARP -> germanLetter(spelling.letterIndex) + "is"
                Accidental.FLAT -> GERMAN_FLATS[spelling.letterIndex]
            }
        }
    }

    private fun germanLetter(letterIndex: Int): String =
        if (letterIndex == B_LETTER_INDEX) "H" else "${LETTERS[letterIndex]}"

    private companion object {
        const val LETTERS = "CDEFGAB"
        const val B_LETTER_INDEX = 6

        /** Ces, Des, Es, Fes, Ges, As, B - the flats as German writes them. */
        val GERMAN_FLATS = listOf("Ces", "Des", "Es", "Fes", "Ges", "As", "B")
    }
}

/**
 * The spelling a musician reaches for first when nothing else has decided.
 *
 * Sharps for the four that are almost always written sharp, and a flat for the one that is almost
 * always written flat: nobody calls it A sharp. This is convention, not theory - real music decides
 * from its key, which is what [AccidentalStyle] is for when a piece has one.
 */
fun Note.conventionalStyle(): AccidentalStyle =
    if (name == NoteName.A_SHARP) AccidentalStyle.FLATS else AccidentalStyle.SHARPS

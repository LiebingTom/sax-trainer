package com.example.saxtrainer.core.music

/**
 * A key: a root, written on its own letter with its own sign, and the scale built on it.
 *
 * The root is a written note without an octave - B flat, never A sharp - because the root's letter
 * decides every other letter: each degree of a seven-note scale sits on the next letter up, so B flat
 * major comes out with an E flat and F sharp major with an E sharp, exactly as the key is written.
 */
data class Key(
    /** C = 0 through B = 6, as [SpelledNote.letterIndex]. */
    val rootLetter: Int,
    val rootAccidental: Accidental,
    val scale: Scale,
) {
    init {
        require(scale.letterSteps != null) { "a key needs a scale that says how it is spelled" }
    }

    /** The root in [octave], as written. */
    private fun rootIn(octave: Int) = SpelledNote(rootLetter, rootAccidental, octave)

    /** "B♭ major", "Fis minor": the root as [naming] calls it, then the scale. */
    fun nameIn(naming: NoteNaming): String {
        // A key is never called "natural minor" - that is the scale's name, not the key's.
        val mode = if (scale == Scales.NATURAL_MINOR) "minor" else scale.name.lowercase()
        return naming.nameOf(rootIn(DISPLAY_OCTAVE)) + " " + mode
    }

    /**
     * Every note of the key from [low] up to [high], both included, written as the key writes it -
     * lowest first. A degree whose spelling would need a double sharp or flat is left out; none of
     * [all] has one.
     */
    fun notesBetween(low: Note, high: Note): List<SpelledNote> =
        (low.octave - 1..high.octave).flatMap { octave ->
            val root = rootIn(octave)
            (1..scale.degreeCount).mapNotNull { degree ->
                val pitch = root.note.transposedBy(scale.semitoneOffset(degree))
                val letters = scale.letterStep(degree) ?: return@mapNotNull null
                SpelledNote.onLetter(root.diatonicIndex + letters, pitch)
            }
        }
            .filter { it.note.midiNumber in low.midiNumber..high.midiNumber }
            .distinct()
            .sortedWith(SpelledNote.PITCH_ORDER)

    companion object {
        private const val DISPLAY_OCTAVE = 4

        private fun major(token: String) = keyOf(token, Scales.MAJOR)
        private fun minor(token: String) = keyOf(token, Scales.NATURAL_MINOR)

        private fun keyOf(root: String, scale: Scale): Key {
            val written = requireNotNull(SpelledNote.parse("$root$DISPLAY_OCTAVE")) {
                "$root is not a note"
            }
            return Key(written.letterIndex, written.accidental, scale)
        }

        /**
         * The keys a player meets: every major and natural minor key with up to six sharps or flats,
         * majors first, each list going round the circle of fifths - sharps, then flats.
         */
        val all: List<Key> = listOf(
            major("C"), major("G"), major("D"), major("A"), major("E"), major("B"),
            major("F#"), major("F"), major("Bb"), major("Eb"), major("Ab"), major("Db"),
            major("Gb"), minor("A"), minor("E"), minor("B"), minor("F#"), minor("C#"),
            minor("G#"), minor("D#"), minor("D"), minor("G"), minor("C"), minor("F"),
            minor("Bb"), minor("Eb"),
        )
    }
}

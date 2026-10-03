package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.Key
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote

/**
 * The saxophone's three registers, by how a note is fingered rather than by octave number: the
 * octave key comes in at D5, not at C5, and the palm keys take over at D6.
 */
enum class Register(val displayName: String, val description: String) {
    LOW("Low", "B♭3 to C♯5, no octave key"),
    MIDDLE("Middle", "D5 to C♯6, with the octave key"),
    PALM("Palm keys", "D6 and up"),
    ;

    companion object {
        private val OCTAVE_KEY_FROM = Note(NoteName.D, 5)
        private val PALM_KEYS_FROM = Note(NoteName.D, 6)

        /** The register a written [note] is played in. */
        fun of(note: Note): Register = when {
            note.midiNumber < OCTAVE_KEY_FROM.midiNumber -> LOW
            note.midiNumber < PALM_KEYS_FROM.midiNumber -> MIDDLE
            else -> PALM
        }
    }
}

/**
 * How a note is written, as a reader sorts them: plain, with a sharp, with a flat - and the rare
 * four, E sharp, B sharp, C flat and F flat, which are white keys written on the wrong letter.
 */
enum class SpellingKind(val displayName: String) {
    NATURAL("Naturals"),
    SHARP("♯"),
    FLAT("♭"),
    RARE("E♯ B♯ C♭ F♭"),
    ;

    companion object {
        private const val C = 0
        private const val E = 2
        private const val F = 3
        private const val B = 6

        fun of(written: SpelledNote): SpellingKind {
            val letter = written.letterIndex
            return when (written.accidental) {
                Accidental.SHARP -> if (letter == E || letter == B) RARE else SHARP
                Accidental.FLAT -> if (letter == C || letter == F) RARE else FLAT
                Accidental.NONE, Accidental.NATURAL -> NATURAL
            }
        }
    }
}

/**
 * Which notes the picker shows: the registers they are played in and the ways they are written. A
 * view filter only - it hides notes, it never chooses or drops them.
 */
data class NoteFilter(
    val registers: Set<Register> = Register.entries.toSet(),
    val spellings: Set<SpellingKind> = DEFAULT_SPELLINGS,
) {
    fun shows(written: SpelledNote): Boolean =
        Register.of(written.note) in registers && SpellingKind.of(written) in spellings

    companion object {
        /** Everything but the rare spellings, which most players never want to drill. */
        val DEFAULT_SPELLINGS: Set<SpellingKind> = SpellingKind.entries.toSet() - SpellingKind.RARE
    }
}

/**
 * The note picker's draft: every note that can be chosen, the ones that are, and how the list is
 * filtered. Pure, so every rule of the window is decided - and tested - here, and the window itself
 * only draws it.
 */
data class NotePickerState(
    /** Every written note that can be chosen, lowest first. */
    val choices: List<SpelledNote>,
    val chosen: Set<SpelledNote>,
    val filter: NoteFilter = NoteFilter(),
    /** The key last filled from, so the window can say which it is. */
    val key: Key = Key.all.first(),
) {
    init {
        require(choices.isNotEmpty()) { "a picker needs something to pick from" }
    }

    /** The notes the filter lets through, in [choices]' order. */
    val shown: List<SpelledNote> get() = choices.filter(filter::shows)

    /** Chosen notes the filter hides: still chosen, just out of sight. */
    val hiddenChosenCount: Int get() = chosen.count { !filter.shows(it) }

    val canApply: Boolean get() = chosen.isNotEmpty()

    fun toggle(note: SpelledNote): NotePickerState =
        copy(chosen = if (note in chosen) chosen - note else chosen + note)

    fun selectAllShown(): NotePickerState = copy(chosen = chosen + shown)

    fun clearShown(): NotePickerState = copy(chosen = chosen - shown.toSet())

    /** Starts over from a ready-made set, which replaces the whole draft. */
    fun startFrom(set: NoteSet, chart: FingeringChart): NotePickerState =
        copy(chosen = NoteSets.notesOf(set, chart, emptySet()).toSet())

    /**
     * Replaces the draft with [key]'s notes in the registers shown, spelled as the key writes them.
     * Whatever spellings that takes are switched on, so nothing chosen this way is hidden: F sharp
     * major's E sharp shows up even though rare spellings are off by default.
     */
    fun useKey(key: Key): NotePickerState {
        val notes = key.notesBetween(choices.first().note, choices.last().note)
            .filter { it in choices && Register.of(it.note) in filter.registers }
        return copy(
            chosen = notes.toSet(),
            key = key,
            filter = filter.copy(spellings = filter.spellings + notes.map(SpellingKind::of)),
        )
    }

    /**
     * Replaces the draft with [notes] - those of a phrase, say - keeping only the ones that can be
     * chosen here. Their registers and spellings are switched on, so nothing chosen this way is
     * hidden.
     */
    fun useNotes(notes: Collection<SpelledNote>): NotePickerState {
        val usable = notes.filter { it in choices }
        return copy(
            chosen = usable.toSet(),
            filter = filter.copy(
                registers = filter.registers + usable.map { Register.of(it.note) },
                spellings = filter.spellings + usable.map(SpellingKind::of),
            ),
        )
    }

    fun toggleRegister(register: Register): NotePickerState = copy(
        filter = filter.copy(registers = filter.registers.toggled(register)),
    )

    fun toggleSpelling(kind: SpellingKind): NotePickerState = copy(
        filter = filter.copy(spellings = filter.spellings.toggled(kind)),
    )

    companion object {
        /** A fresh window over [choices] with [chosen] as the draft; only rare spellings hidden. */
        fun open(choices: List<SpelledNote>, chosen: Set<SpelledNote>): NotePickerState =
            NotePickerState(choices = choices.sortedWith(SpelledNote.PITCH_ORDER), chosen = chosen)
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * The ready-made set [chosen] is exactly, or [NoteSet.CUSTOM] when it is none of them - so a
 * selection made in the picker is stored and summed up the same way as one picked from the list.
 */
fun NoteSets.matchingSet(chosen: Collection<SpelledNote>, chart: FingeringChart): NoteSet {
    val notes = chosen.toSet()
    return NoteSet.entries
        .filter { it != NoteSet.CUSTOM }
        .firstOrNull { notesOf(it, chart, emptySet()).toSet() == notes }
        ?: NoteSet.CUSTOM
}

/**
 * A selection of notes summed up in a line: how many there are and the lowest and highest of them,
 * each named by [nameOf] - "26 notes · C4 – D6".
 */
fun notesDetailOf(notes: List<SpelledNote>, nameOf: (SpelledNote) -> String): String {
    if (notes.isEmpty()) return "No notes chosen yet"
    val sorted = notes.sortedWith(SpelledNote.PITCH_ORDER)
    val count = if (notes.size == 1) "1 note" else "${notes.size} notes"
    return if (notes.size == 1) {
        "$count · ${nameOf(sorted.first())}"
    } else {
        "$count · ${nameOf(sorted.first())} – ${nameOf(sorted.last())}"
    }
}

package com.example.saxtrainer.feature.lexicon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.NoteFingerings
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.fingering.captionOf
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The fingering lexicon: one note at a time, written both ways it can be, with every grip the
 * chart has for it.
 *
 * Notes are written pitch, as the chart is, so the instrument setting changes nothing here: a
 * written G is the same grip on every saxophone.
 */
data class LexiconUiState(
    /** Every note the chart has a grip for, lowest first. */
    val notes: List<Note>,
    val current: Note,
    /** Every grip for [current], the usual one first. */
    val fingerings: NoteFingerings,
    val noteNaming: NoteNaming = NoteNaming.INTERNATIONAL,
    val octaveNumbering: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
) {
    /**
     * [current] as it can be written: with a sharp, and with a flat where that is a different
     * note to read - C sharp and D flat, but D only once.
     */
    val spellings: List<SpelledNote>
        get() = spellingsOf(current)

    val canGoLower: Boolean get() = notes.indexOf(current) > 0

    val canGoHigher: Boolean get() = notes.indexOf(current) < notes.lastIndex

    /** "C♯4 · D♭4", or just "D4". */
    val title: String
        get() = spellings.joinToString(" · ") { noteNaming.labelOf(it, octaveNumbering) }

    /** A chip's text: the letter names without the octave, both of them for a black key. */
    fun chipLabel(note: Note): String = spellingsOf(note).joinToString("/") { noteNaming.nameOf(it) }

    /** The caption under one grip: "Standard", or the keys that set it apart. */
    fun captionOf(index: Int): String = fingerings.captionOf(fingerings.all[index])
}

/** A note written with a sharp, and with a flat when that is a different spelling. */
internal fun spellingsOf(note: Note): List<SpelledNote> =
    listOf(AccidentalStyle.SHARPS, AccidentalStyle.FLATS).map { SpelledNote.of(note, it) }.distinct()

class LexiconViewModel(
    private val settingsRepository: SettingsRepository,
    private val chart: FingeringChart = SaxophoneFingerings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<LexiconUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    noteNaming = settings.noteNaming,
                    octaveNumbering = settings.octaveNumbering,
                )
            }
        }
    }

    /** Shows [note], if the chart has it. */
    fun select(note: Note) {
        val fingerings = chart.fingeringsFor(note) ?: return
        _uiState.value = _uiState.value.copy(current = note, fingerings = fingerings)
    }

    /** One semitone down, stopping at the lowest note there is. */
    fun lower() = step(-1)

    /** One semitone up, stopping at the highest note there is. */
    fun higher() = step(+1)

    private fun step(by: Int) {
        val state = _uiState.value
        val next = state.notes.getOrNull(state.notes.indexOf(state.current) + by) ?: return
        select(next)
    }

    private fun initialState(): LexiconUiState {
        val notes = chart.supportedNotes.sortedBy { it.midiNumber }
        val start = DEFAULT_NOTE.takeIf { chart.supports(it) } ?: notes.first()
        val settings = settingsRepository.settings.value
        return LexiconUiState(
            notes = notes,
            current = start,
            fingerings = requireNotNull(chart.fingeringsFor(start)),
            noteNaming = settings.noteNaming,
            octaveNumbering = settings.octaveNumbering,
        )
    }

    private companion object {
        /** Low-register C: where a beginner's first octave starts. */
        val DEFAULT_NOTE = Note(NoteName.C, 4)
    }
}

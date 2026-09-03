package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Which notes an exercise draws from.
 *
 * Ready-made sets keep the first run simple; [CUSTOM] is for a player who wants to drill a handful
 * of notes. The beginner sets are listed here rather than derived from the fingering chart: the
 * chart covers the whole instrument, while what a beginner should meet first is a teaching decision.
 */
enum class NoteSet(val displayName: String, val description: String) {
    BEGINNER("Beginner", "C to C, no sharps or flats"),
    BEGINNER_PLUS("Beginner plus", "Adds B flat and F sharp"),
    CUSTOM("Custom", "Notes you choose yourself"),
}

object NoteSets {

    /** Written C4 up to C5 without accidentals - the first octave a beginner is taught. */
    private val BEGINNER_NOTES = listOf(
        Note(NoteName.C, 4),
        Note(NoteName.D, 4),
        Note(NoteName.E, 4),
        Note(NoteName.F, 4),
        Note(NoteName.G, 4),
        Note(NoteName.A, 4),
        Note(NoteName.B, 4),
        Note(NoteName.C, 5),
    )

    /** The same octave plus the two accidentals that turn up immediately. */
    private val BEGINNER_PLUS_NOTES = (
        BEGINNER_NOTES + listOf(Note(NoteName.F_SHARP, 4), Note(NoteName.A_SHARP, 4))
        ).sortedBy { it.midiNumber }

    /**
     * The notes [set] practises, in pitch order. Anything the chart does not cover is dropped, so a
     * set can never ask for a note with no fingering to show.
     */
    fun notesOf(
        set: NoteSet,
        chart: FingeringChart,
        customNotes: Set<Note>,
    ): List<Note> = when (set) {
        NoteSet.BEGINNER -> BEGINNER_NOTES.filter(chart::supports)
        NoteSet.BEGINNER_PLUS -> BEGINNER_PLUS_NOTES.filter(chart::supports)
        NoteSet.CUSTOM -> customNotes.filter(chart::supports).sortedBy { it.midiNumber }
    }
}

package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.spelledConventionally

/**
 * Which notes an exercise draws from.
 *
 * Ready-made sets keep the first run simple; [CUSTOM] is for a player who wants to drill a handful
 * of notes. The beginner sets are listed here rather than derived from the fingering chart: the
 * chart covers the whole instrument, while what a beginner should meet first is a teaching decision.
 *
 * This lives beside the chart rather than in one trainer because every note exercise asks the same
 * question - which notes am I working on - and a trainer cannot borrow it from another trainer.
 */
enum class NoteSet(val displayName: String, val description: String) {
    BEGINNER("Beginner", "C to C, no sharps or flats"),
    BEGINNER_PLUS("Beginner plus", "Adds B flat and F sharp"),
    CHROMATIC("Chromatic", "Every note the horn can play, sharps and flats included"),
    CUSTOM("Own selection", "Notes you choose yourself"),
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
     * The written notes [set] practises, in pitch order. Anything the chart does not cover is
     * dropped, so a set can never ask for a note with no fingering to show.
     *
     * The ready-made sets are written the way their notes usually are - the B flat is a B flat. A
     * custom set is written as it was chosen, and may hold one pitch more than once, as D sharp and
     * as E flat.
     */
    fun notesOf(
        set: NoteSet,
        chart: FingeringChart,
        customNotes: Set<SpelledNote>,
    ): List<SpelledNote> = when (set) {
        NoteSet.BEGINNER -> BEGINNER_NOTES.filter(chart::supports).spelledConventionally()
        NoteSet.BEGINNER_PLUS -> BEGINNER_PLUS_NOTES.filter(chart::supports).spelledConventionally()
        NoteSet.CHROMATIC -> chart.supportedNotes.sortedBy { it.midiNumber }.spelledConventionally()
        NoteSet.CUSTOM -> customNotes.filter { chart.supports(it.note) }.sortedWith(SpelledNote.PITCH_ORDER)
    }

    /**
     * Every written note a player can pick from: each pitch the chart covers, in each way it can be
     * written with at most one sharp or flat - C sharp and D flat, E and F flat, B sharp and C.
     */
    fun choicesOf(chart: FingeringChart): List<SpelledNote> =
        chart.supportedNotes.flatMap { SpelledNote.spellingsOf(it) }.sortedWith(SpelledNote.PITCH_ORDER)
}

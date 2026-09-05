package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneStep

/**
 * The predefined practice material.
 *
 * Data, deliberately outside any feature: adding a scale is one entry here and nothing else. Notes
 * are written pitch, so these are the scales as they appear on the player's own part.
 *
 * No note crosses a bar line in any of these - nor in any of them played back down or repeated -
 * because writing one that does needs a tie and this app does not write ties yet. That is a limit of
 * the engraving, not of the model.
 *
 * There is no "up and down" entry: playing a scale back down is a setting the trainer offers for
 * every sequence here, which is strictly more than one more entry would give.
 */
object SequenceLibrary {

    // --- how the definitions below are written ---------------------------------------------

    private val HALF = NotatedDuration(NoteValue.HALF)
    private val QUARTER = NotatedDuration(NoteValue.QUARTER)
    private val DOTTED_QUARTER = NotatedDuration(NoteValue.QUARTER, dots = 1)
    private val EIGHTH = NotatedDuration(NoteValue.EIGHTH)

    private fun note(name: NoteName, octave: Int) = Note(name, octave)

    private infix fun Note.lasting(duration: NotatedDuration) = ToneStep(this, duration.beats())

    /** Every note the same length, which is how a scale is normally practised. */
    private fun List<Note>.each(duration: NotatedDuration) = map { it lasting duration }

    /**
     * A scale in plain quarter notes.
     *
     * Holding the last note longer would read better, but an eight-note scale puts that note on
     * beat seven, where a half note would run across the bar line - and a note split over a bar
     * line is a tie, which this app does not write yet. Quarters throughout is the honest version.
     */
    private fun List<Note>.scale(): List<ToneStep> = each(QUARTER)

    // --- notes ------------------------------------------------------------------------------

    private val C_MAJOR_UP = listOf(
        note(NoteName.C, 4), note(NoteName.D, 4), note(NoteName.E, 4), note(NoteName.F, 4),
        note(NoteName.G, 4), note(NoteName.A, 4), note(NoteName.B, 4), note(NoteName.C, 5),
    )

    private val G_MAJOR_UP = listOf(
        note(NoteName.G, 4), note(NoteName.A, 4), note(NoteName.B, 4), note(NoteName.C, 5),
        note(NoteName.D, 5), note(NoteName.E, 5), note(NoteName.F_SHARP, 5), note(NoteName.G, 5),
    )

    /**
     * F major. The fourth degree is B flat: the app names pitch classes with sharps, so it is stored
     * as A sharp and spelled by [AccidentalStyle.FLATS], which puts it on the B line with a flat in
     * front of it - never on the A line with a sharp.
     */
    private val F_MAJOR_UP = listOf(
        note(NoteName.F, 4), note(NoteName.G, 4), note(NoteName.A, 4), note(NoteName.A_SHARP, 4),
        note(NoteName.C, 5), note(NoteName.D, 5), note(NoteName.E, 5), note(NoteName.F, 5),
    )

    // --- the catalog --------------------------------------------------------------------------

    val cMajorUp = SequenceDefinition(
        id = "c_major_up",
        name = "C Major",
        description = "One octave up, the first scale everyone learns.",
        category = SequenceCategory.SCALE,
        steps = C_MAJOR_UP.scale(),
        suggestedTempo = Tempo(80),
    )

    val gMajorUp = SequenceDefinition(
        id = "g_major_up",
        name = "G Major",
        description = "One octave up, with F sharp.",
        category = SequenceCategory.SCALE,
        steps = G_MAJOR_UP.scale(),
        suggestedTempo = Tempo(80),
    )

    val fMajorUp = SequenceDefinition(
        id = "f_major_up",
        name = "F Major",
        description = "One octave up, with B flat.",
        category = SequenceCategory.SCALE,
        steps = F_MAJOR_UP.scale(),
        accidentalStyle = AccidentalStyle.FLATS,
        suggestedTempo = Tempo(80),
    )

    /** C D E D C - the shape under every scale, worth playing on its own. */
    val upAndBack = SequenceDefinition(
        id = "up_and_back",
        name = "Up and back",
        description = "Three notes up, two back down.",
        category = SequenceCategory.PATTERN,
        steps = listOf(
            note(NoteName.C, 4), note(NoteName.D, 4), note(NoteName.E, 4), note(NoteName.D, 4),
        ).each(QUARTER) + (note(NoteName.C, 4) lasting HALF),
        suggestedTempo = Tempo(72),
    )

    /**
     * A dotted quarter, a lone eighth, a beamed pair and a quarter - one bar that is not a grid of
     * equal notes, which is the point of it.
     */
    val dottedSteps = SequenceDefinition(
        id = "dotted_steps",
        name = "Dotted steps",
        description = "Long, short, two quick ones, then land.",
        category = SequenceCategory.PATTERN,
        steps = listOf(
            note(NoteName.C, 4) lasting DOTTED_QUARTER,
            note(NoteName.D, 4) lasting EIGHTH,
            note(NoteName.E, 4) lasting EIGHTH,
            note(NoteName.F, 4) lasting EIGHTH,
            note(NoteName.G, 4) lasting QUARTER,
        ),
        suggestedTempo = Tempo(66),
    )

    /** Everything, in the order a menu should offer it. */
    val all: List<SequenceDefinition> = listOf(
        cMajorUp,
        gMajorUp,
        fMajorUp,
        upAndBack,
        dottedSteps,
    )

    val byCategory: Map<SequenceCategory, List<SequenceDefinition>> = all.groupBy { it.category }

    fun byId(id: String): SequenceDefinition? = all.firstOrNull { it.id == id }
}

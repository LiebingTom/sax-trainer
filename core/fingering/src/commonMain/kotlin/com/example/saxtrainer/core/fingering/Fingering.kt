package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note

/** Whether a fingering is the one normally taught, or a second way of playing the same note. */
enum class FingeringKind {
    STANDARD,
    ALTERNATE,
}

/**
 * Which keys are held down for one note.
 *
 * This is data about the instrument, not about a picture of it: no positions, sizes or shapes. A
 * renderer decides how to draw the keys, so the same fingering can be shown as a diagram, listed as
 * text, or checked against a future sensor without any of them agreeing on a layout.
 *
 * An empty [pressedKeys] is legal - some notes are played with everything open.
 */
data class Fingering(
    val pressedKeys: Set<SaxKey>,
    val kind: FingeringKind = FingeringKind.STANDARD,
) {
    fun isPressed(key: SaxKey): Boolean = key in pressedKeys

    /** The keys as a chart would print them, e.g. "L1 L2 Side-Bb". */
    val label: String
        get() = if (pressedKeys.isEmpty()) {
            "all open"
        } else {
            SaxKey.entries.filter { it in pressedKeys }.joinToString(" ") { it.label }
        }
}

/**
 * Every way of playing one written note.
 *
 * The standard fingering is what a trainer shows; [alternates] are kept because they are part of
 * how the instrument is really played, and a later feature may offer them even though nothing
 * displays them yet.
 */
data class NoteFingerings(
    val note: Note,
    val standard: Fingering,
    val alternates: List<Fingering> = emptyList(),
) {
    init {
        require(standard.kind == FingeringKind.STANDARD) { "the standard fingering must be standard" }
        require(alternates.all { it.kind == FingeringKind.ALTERNATE }) {
            "alternates must be marked as alternate"
        }
    }

    /** Standard first, then the alternates in the order the chart lists them. */
    val all: List<Fingering> get() = listOf(standard) + alternates

    val hasAlternates: Boolean get() = alternates.isNotEmpty()
}

/**
 * A lookup from written note to fingering.
 *
 * Charts are keyed by the **written** note, which is why one saxophone chart serves the whole
 * family: a written G is the same grip on a tenor and on an alto, it just sounds different. The
 * interface is the place where other instruments can be added later without callers changing.
 */
interface FingeringChart {

    /** Notes this chart knows, in ascending pitch order. */
    val supportedNotes: List<Note>

    /** Every fingering for [writtenNote], or null when the chart does not cover it. */
    fun fingeringsFor(writtenNote: Note): NoteFingerings?

    /** The fingering normally taught for [writtenNote]. */
    fun fingeringFor(writtenNote: Note): Fingering? = fingeringsFor(writtenNote)?.standard

    fun supports(writtenNote: Note): Boolean = fingeringsFor(writtenNote) != null
}

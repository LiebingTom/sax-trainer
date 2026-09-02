package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note

/**
 * Which keys are held down for one note.
 *
 * This is data about the instrument, not about a picture of it: no positions, sizes or shapes. A
 * renderer decides how to draw the keys, so the same fingering can be shown as a diagram, listed as
 * text, or checked against a future sensor without any of them agreeing on a layout.
 */
data class Fingering(
    val pressedKeys: Set<SaxKey>,
) {
    fun isPressed(key: SaxKey): Boolean = key in pressedKeys
}

/**
 * A lookup from written note to fingering.
 *
 * Charts are keyed by the **written** note, which is why one saxophone chart serves the whole
 * family: a written G is the same grip on a tenor and on an alto, it just sounds different. The
 * interface is the place where other instruments, or alternative fingerings for a note, can be added
 * later without callers changing.
 */
interface FingeringChart {

    /** Notes this chart knows, in ascending pitch order. */
    val supportedNotes: List<Note>

    /** The standard fingering for [writtenNote], or null when the chart does not cover it. */
    fun fingeringFor(writtenNote: Note): Fingering?

    fun supports(writtenNote: Note): Boolean = fingeringFor(writtenNote) != null
}

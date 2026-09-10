package com.example.saxtrainer.core.music

import kotlin.math.abs
import kotlin.math.round

/**
 * The sequence cut into bars, as ranges of step indices.
 *
 * A bar begins at the step that starts on a bar line. A step long enough to run past one therefore
 * does not open a new bar - the notation cuts it in two and ties it, but the *step* is one thing and
 * belongs where it started. What comes back is consequently a grouping of steps that never splits a
 * step, which is exactly what a page layout needs: a row of music can be built out of whole groups
 * without a note being torn in half at the end of a line.
 *
 * The first range always starts at 0, and the ranges cover every step in order.
 */
fun ToneSequence.bars(): List<IntRange> {
    val beatsPerBar = timeSignature.beatsPerBar.toDouble()
    val starts = steps.indices.filter { index -> index == 0 || startsBar(index, beatsPerBar) }
    return starts.mapIndexed { position, first ->
        first..((starts.getOrNull(position + 1) ?: steps.size) - 1)
    }
}

/** True when the step at [index] begins where a bar line falls. */
private fun ToneSequence.startsBar(index: Int, beatsPerBar: Double): Boolean {
    val bars = startBeatOf(index).value / beatsPerBar
    return abs(bars - round(bars)) < TOLERANCE
}

/** Beats are doubles; a bar line should not be missed because of the last bit of one. */
private const val TOLERANCE = 1e-9

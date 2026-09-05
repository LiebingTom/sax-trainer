package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlin.math.floor

/**
 * Counting a practice in: four, three, two, one, play.
 *
 * Counted in beats rather than seconds, because that is what a player counts and because it then
 * means the same thing at every tempo. A practice with a tempo of its own counts at that tempo; one
 * without counts at whatever reference speed it already uses to turn beats into time, which is the
 * same conversion the exercise itself runs on.
 *
 * How many beats is a setting rather than a constant here: a practice that brings its own count-in -
 * Long Tones counts a bar, a sequence counts the metronome in - keeps it, and everything else asks
 * `AppSettings.countInBeats`. New practices should call this before their first note rather than
 * starting the instant the button is pressed.
 *
 * Before the count itself there is a short quiet lead-in - see [leadInBeatsFor] - so the player has
 * a moment to lift the horn between pressing Start and the first counted beat.
 */

/**
 * Quiet beats between pressing Start and the count starting: half the count, rounded down.
 *
 * Pressing Start and being counted in the same instant leaves no time to get the horn up, and
 * making that gap a fixed number of milliseconds would feel long at 60 BPM and gone at 200. Half
 * the count keeps it in proportion: four counted beats get two quiet ones, two get one, and a
 * practice with no count-in gets none - nothing to be in proportion to, and the player pressed
 * Start because they were ready.
 *
 * It is deliberately silent even when the count is clicked. The first thing heard is then the first
 * counted beat, so the player counts from a click rather than having to work out which of a run of
 * identical clicks the count started on.
 */
fun leadInBeatsFor(countInBeats: Int): Int = countInBeats / 2

/**
 * Waits [beats] out silently at [tempo], emitting nothing.
 *
 * On a clock rather than the metronome because there is nothing to hear: this is the gap before the
 * count, and a clicked count starts its engine afterwards on a beat grid of its own.
 */
suspend fun BeatClock.leadIn(beats: Int, tempo: Tempo) {
    if (beats <= 0) return
    elapsedBeats(tempo).first { elapsed -> elapsed.value >= beats }
}

/**
 * Counts [beats] down to one at [tempo], calling [onCount] with the number still to come.
 *
 * Returns once the last beat has passed, at which point the exercise starts. A count of zero or less
 * returns immediately without ever calling [onCount], which is how "no count-in" is expressed.
 */
suspend fun BeatClock.countIn(beats: Int, tempo: Tempo, onCount: (Int) -> Unit) {
    if (beats <= 0) return
    elapsedBeats(tempo)
        .map { elapsed -> beats - floor(elapsed.value).toInt() }
        .distinctUntilChanged()
        .takeWhile { remaining -> remaining > 0 }
        .collect(onCount)
}

package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneSequence
import kotlin.math.abs

/** Whether a note arrived when it was written to. Says nothing about which note was played. */
enum class NoteTiming(val displayName: String) {
    ON_TIME("On time"),
    EARLY("Early"),
    LATE("Late"),
    NOT_PLAYED("Not played"),
}

/**
 * How far from the written beat still counts as on it.
 *
 * In seconds rather than beats, because hearing is: a listener notices roughly the same absolute
 * lateness at 60 and at 160 beats per minute, so a tolerance expressed as a fraction of a beat would
 * be lenient at slow tempos and impossible at fast ones.
 *
 * [windowSeconds] is the wider net used to decide which written note a sound belongs to at all;
 * anything further away is somebody else's note, or nobody's.
 */
data class TimingTolerance(
    val onTimeSeconds: Double = DEFAULT_ON_TIME_SECONDS,
    val windowSeconds: Double = DEFAULT_WINDOW_SECONDS,
) {
    init {
        require(onTimeSeconds > 0) { "the on-time window needs some width" }
        require(windowSeconds >= onTimeSeconds) { "the outer window cannot be tighter than the inner" }
    }

    /**
     * How [onsetSeconds] compares with the moment the note was written for. A null onset means
     * nothing was heard, which is not the same as being late.
     */
    fun judge(expectedSeconds: Double, onsetSeconds: Double?): NoteTiming {
        if (onsetSeconds == null) return NoteTiming.NOT_PLAYED
        val error = onsetSeconds - expectedSeconds
        return when {
            // The edge of the window counts as inside it; EPSILON keeps that true of a value that
            // is only outside because of how doubles are represented.
            abs(error) <= onTimeSeconds + EPSILON -> NoteTiming.ON_TIME
            error < 0 -> NoteTiming.EARLY
            else -> NoteTiming.LATE
        }
    }

    /** True while [onsetSeconds] is close enough to be this note's attack rather than another's. */
    fun belongsToNote(expectedSeconds: Double, onsetSeconds: Double): Boolean =
        abs(onsetSeconds - expectedSeconds) <= windowSeconds + EPSILON

    private companion object {
        const val DEFAULT_ON_TIME_SECONDS = 0.12
        const val DEFAULT_WINDOW_SECONDS = 0.5

        /** Far below anything a player could hear; only there to make the boundary reliable. */
        const val EPSILON = 1e-9
    }
}

/**
 * When each note of a sequence is due, in seconds from the first note.
 *
 * The sequence itself stores beats and nothing else, so the same material is playable at any speed;
 * this is the one place the two meet, and it is a function of the tempo rather than anything stored.
 */
fun ToneSequence.expectedSecondsOf(index: Int, tempo: Tempo): Double =
    tempo.secondsFor(startBeatOf(index))

/** Total playing time at [tempo], count-in excluded. */
fun ToneSequence.totalSeconds(tempo: Tempo): Double = tempo.secondsFor(totalBeats)

/** Beats elapsed once the step at [index] has been played out. */
fun ToneSequence.endBeatOf(index: Int): Beats =
    Beats(startBeatOf(index).value + steps[index].duration.value)

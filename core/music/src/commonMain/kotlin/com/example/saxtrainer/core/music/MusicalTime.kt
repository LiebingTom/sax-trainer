package com.example.saxtrainer.core.music

import kotlin.jvm.JvmInline
import kotlin.math.floor

/**
 * A musical duration in beats.
 *
 * Musical material is measured in beats rather than seconds so the same material can be played at
 * any tempo; seconds only appear where the music meets a clock, through [Tempo].
 */
@JvmInline
value class Beats(val value: Double) : Comparable<Beats> {

    init {
        require(value >= 0.0) { "beats cannot be negative" }
    }

    operator fun plus(other: Beats): Beats = Beats(value + other.value)

    operator fun times(factor: Int): Beats = Beats(value * factor)

    override fun compareTo(other: Beats): Int = value.compareTo(other.value)

    override fun toString(): String = "$value beats"

    companion object {
        val ZERO = Beats(0.0)

        fun of(count: Int): Beats = Beats(count.toDouble())
    }
}

/**
 * How beats are grouped into bars.
 *
 * Only grouping is modelled: the time signature says where a bar line falls, it deliberately does
 * not scale any duration, so a step of four beats lasts four beats in 4/4 and in 3/4 alike.
 */
data class TimeSignature(
    val beatsPerBar: Int,
    val beatUnit: Int,
) {
    init {
        require(beatsPerBar > 0) { "beatsPerBar must be positive" }
        require(beatUnit > 0) { "beatUnit must be positive" }
    }

    override fun toString(): String = "$beatsPerBar/$beatUnit"

    companion object {
        val FOUR_FOUR = TimeSignature(4, 4)
    }
}

/** Where a point in time falls in the bar structure; both values are zero based. */
data class BarPosition(
    val bar: Int,
    val beatInBar: Double,
)

/**
 * Playing speed in beats per minute.
 *
 * The only place where musical time becomes clock time. Features convert at their boundary and keep
 * measuring in whatever clock they trust - in this app, audio time.
 */
data class Tempo(val bpm: Int) {

    init {
        require(bpm > 0) { "bpm must be positive" }
    }

    fun secondsFor(beats: Beats): Double = beats.value * SECONDS_PER_MINUTE / bpm

    fun beatsFor(seconds: Double): Beats = Beats(seconds * bpm / SECONDS_PER_MINUTE)

    companion object {
        /** Range offered in the UI; matches what the metronome supports. */
        val SUPPORTED_RANGE = 40..240

        private const val SECONDS_PER_MINUTE = 60.0
    }
}

/** Bar and beat of a position [beats] into a piece written in this time signature. */
fun TimeSignature.positionAt(beats: Beats): BarPosition {
    val bar = floor(beats.value / beatsPerBar).toInt()
    return BarPosition(bar = bar, beatInBar = beats.value - bar * beatsPerBar)
}

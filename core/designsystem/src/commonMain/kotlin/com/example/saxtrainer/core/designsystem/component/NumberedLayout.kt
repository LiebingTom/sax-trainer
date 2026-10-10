package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.ScaleDegree
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.bars
import kotlin.math.abs
import kotlin.math.floor

/** What one place in numbered notation shows. */
sealed interface NumberedSymbol {
    /** A note as its degree in the key; null where the material did not say, drawn as a gap mark. */
    data class Number(val degree: ScaleDegree?) : NumberedSymbol

    /** A beat more of the note before it. */
    data object Dash : NumberedSymbol

    /** A beat, or part of one, of silence. */
    data object Rest : NumberedSymbol
}

/**
 * One symbol of numbered notation, with how long it is written: [underlines] halve it (one for an
 * eighth, two for a sixteenth) and [dotted] adds half again.
 */
data class NumberedToken(
    val symbol: NumberedSymbol,
    /** The step of the sequence this belongs to: a dash belongs to the note it lengthens. */
    val stepIndex: Int,
    /** The beat of the bar it starts in: tokens of one beat are drawn together, their underlines joined. */
    val beat: Int,
    val underlines: Int = 0,
    val dotted: Boolean = false,
)

/** One bar of numbered notation: the steps it holds and how they are written. */
data class NumberedBar(val steps: IntRange, val tokens: List<NumberedToken>)

/**
 * A sequence as numbered notation (jianpu), worked out without drawing anything.
 *
 * A note a beat long is its number alone. Shorter is underlined - once for an eighth, twice for a
 * sixteenth - and longer gets a dash for every further beat, so a half note is "5 –" and a whole
 * one "1 – – –". A dot after it is the dotted value. A rest is a 0, written the same way except that
 * a long one is a 0 for every beat rather than a dash, since there is nothing to hold.
 */
object NumberedLayout {

    fun of(sequence: ToneSequence): List<NumberedBar> = sequence.bars().map { range ->
        val barStart = sequence.startBeatOf(range.first).value
        NumberedBar(
            steps = range,
            tokens = range.flatMap { index ->
                tokensOf(sequence.steps[index], index, sequence.startBeatOf(index).value - barStart)
            },
        )
    }

    private fun tokensOf(step: ToneStep, index: Int, startInBar: Double): List<NumberedToken> {
        val head = if (step.isRest) NumberedSymbol.Rest else NumberedSymbol.Number(step.degree)
        val beat = floor(startInBar + TOLERANCE).toInt()
        val length = step.duration.value

        if (length < 1 - TOLERANCE) {
            val (underlines, dotted) = shortValueOf(length)
            return listOf(NumberedToken(head, index, beat, underlines, dotted))
        }

        val beats = floor(length + TOLERANCE).toInt()
        val dotted = abs(length - beats - HALF) < TOLERANCE
        val held = if (step.isRest) NumberedSymbol.Rest else NumberedSymbol.Dash
        val tokens = listOf(NumberedToken(head, index, beat)) +
            (1 until beats).map { NumberedToken(held, index, beat + it) }
        // The dot goes last, after the dashes: "5 – ·" is two and a half beats.
        return if (dotted) tokens.dropLast(1) + tokens.last().copy(dotted = true) else tokens
    }

    /** Underlines and dot for a value shorter than a beat: an eighth is one line, a dotted eighth one and a dot. */
    private fun shortValueOf(length: Double): Pair<Int, Boolean> {
        var plain = HALF
        for (underlines in 1..MAX_UNDERLINES) {
            if (abs(length - plain) < TOLERANCE) return underlines to false
            if (abs(length - plain * DOT) < TOLERANCE) return underlines to true
            plain /= 2
        }
        // Not a value numbered notation writes on its own; the nearest shape is better than none.
        return 1 to false
    }

    private const val HALF = 0.5
    private const val DOT = 1.5
    private const val MAX_UNDERLINES = 4
    private const val TOLERANCE = 1e-9
}

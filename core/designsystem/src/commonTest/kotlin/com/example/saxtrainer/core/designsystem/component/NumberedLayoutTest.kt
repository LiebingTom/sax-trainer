package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ScaleDegree
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals

/** How lengths are written in numbered notation, worked out without drawing. */
class NumberedLayoutTest {

    private fun note(beats: Double, number: Int = 1) = ToneStep(Note(NoteName.C, 4), Beats(beats), degree = ScaleDegree(number))
    private fun rest(beats: Double) = ToneStep.rest(Beats(beats))

    /** Each token as text: "1" a number, "-" a dash, "0" a rest, then "_" per underline and "." for a dot. */
    private fun written(vararg steps: ToneStep): List<String> =
        NumberedLayout.of(ToneSequence(steps.toList())).map { bar ->
            bar.tokens.joinToString(" ") { token ->
                val head = when (val symbol = token.symbol) {
                    is NumberedSymbol.Number -> symbol.degree?.label ?: "?"
                    NumberedSymbol.Dash -> "-"
                    NumberedSymbol.Rest -> "0"
                }
                head + "_".repeat(token.underlines) + if (token.dotted) "." else ""
            }
        }

    @Test
    fun `a quarter is the number alone, an eighth underlined once, a sixteenth twice`() {
        assertEquals(
            listOf("1 2_ 3_ 4__ 5__ 6__ 7__ 1"),
            written(note(1.0, 1), note(0.5, 2), note(0.5, 3), note(0.25, 4), note(0.25, 5), note(0.25, 6), note(0.25, 7), note(1.0)),
        )
    }

    @Test
    fun `a half note gets one dash, a whole note three`() {
        assertEquals(listOf("5 - 3 -", "1 - - -"), written(note(2.0, 5), note(2.0, 3), note(4.0)))
    }

    @Test
    fun `dotted values put the dot last`() {
        assertEquals(listOf("1. 2_ 3 -"), written(note(1.5), note(0.5, 2), note(2.0, 3)))
        assertEquals(listOf("1 - - 2"), written(note(3.0), note(1.0, 2)))
        assertEquals(listOf("1_ 2_ 3_ 5 -."), written(note(0.5), note(0.5, 2), note(0.5, 3), note(2.5, 5)))
        assertEquals(listOf("1_. 2__ 3 - -"), written(note(0.75), note(0.25, 2), note(3.0, 3)))
    }

    @Test
    fun `a rest is a 0 for every beat`() {
        assertEquals(listOf("1 0 0 0", "0_ 1_ 0 0 0"), written(note(1.0), rest(3.0), rest(0.5), note(0.5), rest(3.0)))
    }

    @Test
    fun `tokens are grouped by the beat they start in`() {
        val bar = NumberedLayout.of(ToneSequence(listOf(note(0.5), note(0.5), note(0.5), note(2.5)))).single()
        assertEquals(listOf(0, 0, 1, 1, 2), bar.tokens.map { it.beat })
        assertEquals(listOf(0, 1, 2, 3, 3), bar.tokens.map { it.stepIndex })
    }

    @Test
    fun `every bar is its own group of steps`() {
        val bars = NumberedLayout.of(ToneSequence(listOf(note(4.0), note(2.0), note(2.0), note(1.0), rest(3.0))))
        assertEquals(listOf(0..0, 1..2, 3..4), bars.map { it.steps })
    }
}

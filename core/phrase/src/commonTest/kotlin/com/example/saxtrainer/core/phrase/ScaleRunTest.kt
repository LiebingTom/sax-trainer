package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The scale up and down, generated for whichever scale is picked, a rest filling the last bar. */
class ScaleRunTest {

    private val eighth = Beats(0.5)
    private val run = PhraseRecipe(id = "run", name = "Up & down", description = "", run = ScaleRun(eighth))

    @Test
    fun theBluesRunsItsSixNotesUpAndDownThenRestsToTheBarLine() {
        val phrase = run.resolve(TestScales.BLUES, Note(NoteName.A, 4))

        assertEquals(2, phrase.bars.size)
        assertEquals(
            listOf("A4", "C5", "D5", "Eb5", "E5", "G5", "A5", "G5", "E5", "Eb5", "D5", "C5", "A4"),
            phrase.toneSequence().writtenNotes.map { it.token },
        )
        val last = phrase.bars.last().steps.last()
        assertNull(last.note, "the run ends on a rest")
        assertEquals(Beats(1.5), last.duration)
    }

    @Test
    fun majorRunsItsSevenNotesAndRestsHalfABeat() {
        val phrase = run.resolve(TestScales.MAJOR, Note(NoteName.C, 4))

        assertEquals(2, phrase.bars.size)
        assertEquals(15, phrase.toneSequence().writtenNotes.size)
        assertEquals(Beats(0.5), phrase.bars.last().steps.last().duration)
        assertNull(phrase.bars.last().steps.last().note)
    }

    @Test
    fun everyBarOfARunIsFull() {
        listOf(TestScales.MAJOR, TestScales.NATURAL_MINOR, TestScales.BLUES).forEach { scale ->
            run.resolve(scale, Note(NoteName.C, 4)).bars.forEach { bar ->
                assertEquals(4.0, bar.steps.sumOf { it.duration.value }, scale.id)
            }
        }
    }

    @Test
    fun theRestFillsWhateverTheLastBarLacks() {
        // Fifteen half notes: seven full bars, then the closing root and a two-beat rest.
        val bars = ScaleRun(Beats.of(2)).barsOn(TestScales.MAJOR, TimeSignature.FOUR_FOUR)

        assertEquals(8, bars.size)
        assertEquals(listOf(1, null), bars.last().steps.map { it.degree })
        assertEquals(Beats.of(2), bars.last().steps.last().duration)
    }

    @Test
    fun aPatternIsBarsOrARunNeverBothOrNeither() {
        val bar = RecipeBar(listOf(RecipeStep(1, Beats.of(4))))
        assertFailsWith<IllegalArgumentException> {
            PhraseRecipe(id = "both", name = "", description = "", bars = listOf(bar), run = ScaleRun(eighth))
        }
        assertFailsWith<IllegalArgumentException> {
            PhraseRecipe(id = "neither", name = "", description = "")
        }
    }

    @Test
    fun aRunWhoseNotesDoNotFillABarEvenlyIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            PhraseRecipe(id = "odd", name = "", description = "", run = ScaleRun(Beats(1.5)))
        }
    }

    @Test
    fun aRunIsReadFromJson() {
        val pattern = parsePatterns(
            listOf("""{ "id": "run", "name": "Run", "trainingBars": 2, "run": { "duration": "eighth" } }"""),
        ).single()

        assertEquals(ScaleRun(eighth), pattern.run)
        assertEquals(emptyList(), pattern.bars)
    }
}

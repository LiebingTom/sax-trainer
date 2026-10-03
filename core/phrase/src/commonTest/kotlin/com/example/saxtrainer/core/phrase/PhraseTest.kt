package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhraseTest {

    private val quarter = NotatedDuration(NoteValue.QUARTER).beats()
    private fun step(name: NoteName, octave: Int = 4) = ToneStep(Note(name, octave), quarter)

    private fun bar(vararg names: NoteName) = PhraseBar(names.map { step(it) })

    @Test
    fun aBarMustFillExactlyOneBar() {
        assertFailsWith<IllegalArgumentException> {
            Phrase(id = "short", name = "Short", description = "", bars = listOf(PhraseBar(listOf(step(NoteName.C)))))
        }
    }

    @Test
    fun aPhraseNeedsAtLeastOneTrainingBar() {
        assertFailsWith<IllegalArgumentException> {
            Phrase(
                id = "p",
                name = "P",
                description = "",
                bars = listOf(bar(NoteName.C, NoteName.D, NoteName.E, NoteName.F)),
                trainingBars = 0,
            )
        }
    }

    @Test
    fun unitsChunkTheBarsByTrainingBars() {
        val phrase = PhraseLibrary.byId("six_bar_walk")!!

        assertEquals(2, phrase.trainingBars)
        assertEquals(
            listOf(PracticeUnit(0..1), PracticeUnit(2..3), PracticeUnit(4..5)),
            phrase.units,
        )
    }

    @Test
    fun theLastUnitIsWhateverIsLeftOver() {
        val phrase = Phrase(
            id = "p",
            name = "P",
            description = "",
            bars = List(5) { bar(NoteName.C, NoteName.D, NoteName.E, NoteName.F) },
            trainingBars = 2,
        )

        assertEquals(listOf(PracticeUnit(0..1), PracticeUnit(2..3), PracticeUnit(4..4)), phrase.units)
    }

    @Test
    fun aPhraseWithOneTrainingBarHasOneUnitPerBar() {
        val phrase = Phrase(
            id = "p",
            name = "P",
            description = "",
            bars = listOf(
                bar(NoteName.C, NoteName.D, NoteName.E, NoteName.F),
                bar(NoteName.G, NoteName.A, NoteName.B, NoteName.C),
            ),
        )

        assertEquals(listOf(PracticeUnit(0..0), PracticeUnit(1..1)), phrase.units)
    }

    @Test
    fun toneSequenceOfAUnitIsJustThatUnitsBars() {
        val phrase = PhraseLibrary.byId("six_bar_walk")!!
        val secondBar = PracticeUnit(1..1)

        val sequence = phrase.toneSequenceOf(secondBar)

        assertEquals(4, sequence.steps.size)
        assertEquals(phrase.bars[1].steps, sequence.steps)
    }

    @Test
    fun toneSequenceIsEveryBarInOrder() {
        val phrase = PhraseLibrary.byId("six_bar_walk")!!

        val sequence = phrase.toneSequence()

        assertEquals(phrase.bars.sumOf { it.steps.size }, sequence.steps.size)
        assertEquals(phrase.bars.first().steps.first(), sequence.steps.first())
        assertEquals(phrase.bars.last().steps.last(), sequence.steps.last())
    }

    @Test
    fun itsNotesAreEachTakenOnceLowestFirstAsWritten() {
        val phrase = Phrase(
            id = "p",
            name = "P",
            description = "",
            bars = listOf(
                bar(NoteName.E, NoteName.C, NoteName.E, NoteName.A_SHARP),
                PhraseBar(listOf(ToneStep.rest(quarter), step(NoteName.C), step(NoteName.D), step(NoteName.C, 5))),
            ),
        )

        assertEquals(listOf("C4", "D4", "E4", "A#4", "C5"), phrase.distinctWrittenNotes().map { it.token })
    }
}

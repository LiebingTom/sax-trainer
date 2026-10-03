package com.example.saxtrainer.core.phrase


import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhraseRecipeTest {

    private val quarter = Beats.of(1)
    private fun bar(vararg degrees: Int) = RecipeBar(degrees.map { RecipeStep(it, quarter) })

    /**
     * The degree pattern is resolved against whichever scale and root were chosen, not the scale's
     * own ascending order: D major's 1-2-3-5 is D-E-F#-A, and the same pattern against C major is
     * C-D-E-G, because the *degrees* are the same but the *scale* is not.
     */
    @Test
    fun theSamePatternResolvesDifferentlyInDifferentKeys() {
        val pattern = PhraseRecipe(id = "p", name = "1-2-3-5", description = "", bars = listOf(bar(1, 2, 3, 5)))

        val dMajor = pattern.resolve(TestScales.MAJOR, Note(NoteName.D, 4))
        assertEquals(
            listOf(Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4)),
            dMajor.toneSequence().notes,
        )

        val cMajor = pattern.resolve(TestScales.MAJOR, Note(NoteName.C, 4))
        assertEquals(
            listOf(Note(NoteName.C, 4), Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.G, 4)),
            cMajor.toneSequence().notes,
        )
    }

    @Test
    fun theOctaveComesFromTheRootNotThePattern() {
        val pattern = PhraseRecipe(id = "p", name = "1-3-5", description = "", bars = listOf(bar(1, 3, 5, 1)))

        val resolved = pattern.resolve(TestScales.MAJOR, Note(NoteName.C, 5))

        assertEquals(
            listOf(Note(NoteName.C, 5), Note(NoteName.E, 5), Note(NoteName.G, 5), Note(NoteName.C, 5)),
            resolved.toneSequence().notes,
        )
    }

    @Test
    fun aRestStaysARestOnceResolved() {
        val pattern = PhraseRecipe(
            id = "p",
            name = "with a rest",
            description = "",
            bars = listOf(RecipeBar(listOf(RecipeStep(1, quarter), RecipeStep(null, quarter), RecipeStep(3, quarter), RecipeStep(5, quarter)))),
        )

        val resolved = pattern.resolve(TestScales.MAJOR, Note(NoteName.C, 4))

        assertEquals(listOf(true, false, true, true), resolved.bars.single().steps.map { !it.isRest })
    }

    @Test
    fun resolvedPhraseKeepsTheRecipesTrainingBars() {
        val pattern = PhraseRecipe(id = "p", name = "p", description = "", bars = listOf(bar(1, 2, 3, 5), bar(5, 3, 2, 1)), trainingBars = 2)

        assertEquals(2, pattern.resolve(TestScales.MAJOR, Note(NoteName.C, 4)).trainingBars)
    }

    @Test
    fun aBarMustFillExactlyOneBar() {
        assertFailsWith<IllegalArgumentException> {
            PhraseRecipe(id = "p", name = "p", description = "", bars = listOf(bar(1)))
        }
    }

    // --- written spelling ------------------------------------------------------------------------

    @Test
    fun aSevenNoteScaleWritesEachDegreeOnItsOwnLetter() {
        val pattern = PhraseRecipe(id = "p", name = "up", description = "", bars = listOf(bar(1, 2, 3, 4), bar(5, 6, 7, 8)))

        val fMajor = pattern.resolve(TestScales.MAJOR, Note(NoteName.F, 4)).toneSequence()

        assertEquals(
            listOf("F4", "G4", "A4", "Bb4", "C5", "D5", "E5", "F5"),
            fMajor.writtenNotes.map { it.token },
            "the fourth of F major is a B flat, whatever the phrase's fallback style",
        )

        val cMinor = pattern.resolve(TestScales.NATURAL_MINOR, Note(NoteName.C, 4)).toneSequence()
        assertEquals(listOf("C4", "D4", "Eb4", "F4", "G4", "Ab4", "Bb4", "C5"), cMinor.writtenNotes.map { it.token })

        val dMajor = pattern.resolve(TestScales.MAJOR, Note(NoteName.D, 4)).toneSequence()
        assertEquals(listOf("D4", "E4", "F#4", "G4", "A4", "B4", "C#5", "D5"), dMajor.writtenNotes.map { it.token })
    }

    @Test
    fun theBluesIsWrittenWithItsFlatsNotRespelledAsSharps() {
        val pattern = PhraseRecipe(id = "p", name = "blues", description = "", bars = listOf(bar(1, 2, 3, 4), bar(5, 6, 7, 1)))

        val cBlues = pattern.resolve(TestScales.BLUES, Note(NoteName.C, 4)).toneSequence()
        assertEquals(
            listOf("C4", "Eb4", "F4", "Gb4", "G4", "Bb4", "C5", "C4"),
            cBlues.writtenNotes.map { it.token },
        )

        val aBlues = pattern.resolve(TestScales.BLUES, Note(NoteName.A, 4)).toneSequence()
        assertEquals(listOf("A4", "C5", "D5", "Eb5", "E5", "G5", "A5", "A4"), aBlues.writtenNotes.map { it.token })
    }

    @Test
    fun aScaleThatDoesNotSayHowItIsSpelledFallsBackToThePhrasesStyle() {
        val wholeTone = com.example.saxtrainer.core.music.Scale(
            id = "whole_tone",
            name = "Whole tone",
            intervals = listOf(0, 2, 4, 6, 8, 10),
        )
        val pattern = PhraseRecipe(id = "p", name = "up", description = "", bars = listOf(bar(1, 2, 3, 4)))

        val sequence = pattern.resolve(wholeTone, Note(NoteName.C, 4)).toneSequence()

        assertEquals(listOf(null, null, null, null), sequence.steps.map { it.written }, "no spelling decided for it")
        assertEquals(listOf("C4", "D4", "E4", "F#4"), sequence.writtenNotes.map { it.token })
    }
}

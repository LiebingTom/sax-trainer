package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scales
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

        val dMajor = pattern.resolve(Scales.MAJOR, Note(NoteName.D, 4))
        assertEquals(
            listOf(Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4)),
            dMajor.toneSequence().notes,
        )

        val cMajor = pattern.resolve(Scales.MAJOR, Note(NoteName.C, 4))
        assertEquals(
            listOf(Note(NoteName.C, 4), Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.G, 4)),
            cMajor.toneSequence().notes,
        )
    }

    @Test
    fun theOctaveComesFromTheRootNotThePattern() {
        val pattern = PhraseRecipe(id = "p", name = "1-3-5", description = "", bars = listOf(bar(1, 3, 5, 1)))

        val resolved = pattern.resolve(Scales.MAJOR, Note(NoteName.C, 5))

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

        val resolved = pattern.resolve(Scales.MAJOR, Note(NoteName.C, 4))

        assertEquals(listOf(true, false, true, true), resolved.bars.single().steps.map { !it.isRest })
    }

    @Test
    fun resolvedPhraseKeepsTheRecipesTrainingBars() {
        val pattern = PhraseRecipe(id = "p", name = "p", description = "", bars = listOf(bar(1, 2, 3, 5), bar(5, 3, 2, 1)), trainingBars = 2)

        assertEquals(2, pattern.resolve(Scales.MAJOR, Note(NoteName.C, 4)).trainingBars)
    }

    @Test
    fun aBarMustFillExactlyOneBar() {
        assertFailsWith<IllegalArgumentException> {
            PhraseRecipe(id = "p", name = "p", description = "", bars = listOf(bar(1)))
        }
    }
}

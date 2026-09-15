package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhraseSourceTest {

    @Test
    fun libraryResolvesAKnownPhraseFromTheCatalog() {
        val source = PhraseSource.Library("six_bar_walk")

        assertEquals(PhraseLibrary.byId("six_bar_walk"), source.resolve(PhraseLibrary, PatternLibrary))
    }

    @Test
    fun libraryResolvesToNullForAnUnknownId() {
        val source = PhraseSource.Library("does_not_exist")

        assertNull(source.resolve(PhraseLibrary, PatternLibrary))
    }

    @Test
    fun scalePatternResolvesAStoredRecipeAgainstTheChosenScaleAndRoot() {
        val source = PhraseSource.ScalePattern(
            patternId = "one_two_three_five",
            scale = Scales.MAJOR,
            root = NoteName.D,
            octaves = setOf(4),
        )

        val phrase = source.resolve(PhraseLibrary, PatternLibrary)

        assertEquals(
            listOf(Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4)),
            phrase?.toneSequence()?.notes,
        )
    }

    @Test
    fun scalePatternWithSeveralOctavesConcatenatesEachOctavesBarsInOrder() {
        val source = PhraseSource.ScalePattern(
            patternId = "one_two_three_five",
            scale = Scales.MAJOR,
            root = NoteName.D,
            octaves = setOf(5, 4), // unsorted on purpose: resolution sorts them
        )

        val phrase = source.resolve(PhraseLibrary, PatternLibrary)

        assertEquals(
            listOf(
                Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4),
                Note(NoteName.D, 5), Note(NoteName.E, 5), Note(NoteName.F_SHARP, 5), Note(NoteName.A, 5),
            ),
            phrase?.toneSequence()?.notes,
        )
    }

    @Test
    fun scalePatternResolvesToNullForAnUnknownPatternId() {
        val source = PhraseSource.ScalePattern(
            patternId = "does_not_exist",
            scale = Scales.MAJOR,
            root = NoteName.C,
            octaves = setOf(4),
        )

        assertNull(source.resolve(PhraseLibrary, PatternLibrary))
    }
}

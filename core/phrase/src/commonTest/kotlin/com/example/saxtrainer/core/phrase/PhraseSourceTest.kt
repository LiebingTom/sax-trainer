package com.example.saxtrainer.core.phrase



import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhraseSourceTest {

    @Test
    fun libraryResolvesAKnownPhraseFromTheCatalog() {
        val source = PhraseSource.Library("six_bar_walk")

        assertEquals(PhraseLibrary.byId("six_bar_walk"), source.resolve(PhraseLibrary, TestPatterns.catalog))
    }

    @Test
    fun libraryResolvesToNullForAnUnknownId() {
        val source = PhraseSource.Library("does_not_exist")

        assertNull(source.resolve(PhraseLibrary, TestPatterns.catalog))
    }

    @Test
    fun scalePatternResolvesAStoredRecipeAgainstTheChosenScaleAndRoot() {
        val source = PhraseSource.ScalePattern(
            patternId = TestPatterns.WALK_ID,
            scale = TestScales.MAJOR,
            root = NoteName.D,
            octaves = setOf(4),
        )

        val phrase = source.resolve(PhraseLibrary, TestPatterns.catalog)

        // The pattern's own first bar - degrees 1, 2, 3, 5.
        assertEquals(
            listOf(Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4)),
            phrase?.toneSequence()?.notes?.take(4),
        )
    }

    @Test
    fun scalePatternWithSeveralOctavesConcatenatesEachOctavesBarsInOrder() {
        val source = PhraseSource.ScalePattern(
            patternId = TestPatterns.WALK_ID,
            scale = TestScales.MAJOR,
            root = NoteName.D,
            octaves = setOf(5, 4), // unsorted on purpose: resolution sorts them
        )

        val phrase = source.resolve(PhraseLibrary, TestPatterns.catalog)
        val notes = phrase?.toneSequence()?.notes

        val octave4FirstBar = listOf(Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.F_SHARP, 4), Note(NoteName.A, 4))
        val octave5FirstBar = listOf(Note(NoteName.D, 5), Note(NoteName.E, 5), Note(NoteName.F_SHARP, 5), Note(NoteName.A, 5))
        assertEquals(octave4FirstBar, notes?.take(4), "octave 4 comes first")
        assertEquals(octave5FirstBar, notes?.drop(notes.size / 2)?.take(4), "octave 5's own bars follow, not woven in")
    }

    @Test
    fun scalePatternResolvesToNullForAnUnknownPatternId() {
        val source = PhraseSource.ScalePattern(
            patternId = "does_not_exist",
            scale = TestScales.MAJOR,
            root = NoteName.C,
            octaves = setOf(4),
        )

        assertNull(source.resolve(PhraseLibrary, TestPatterns.catalog))
    }
}

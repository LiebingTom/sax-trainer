package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scales read from content files, and every one of them offered on every root. */
class ScaleContentTest {

    @Test
    fun aScaleFileIsReadWithItsLetters() {
        val scale = parseScales(
            listOf("""{ "id": "blues", "name": "Blues", "intervals": [0, 3, 5, 6, 7, 10], "letters": [0, 2, 3, 4, 4, 6] }"""),
        ).single()

        assertEquals(TestScales.BLUES, scale)
    }

    @Test
    fun aSevenNoteScaleNeedsNoLetters() {
        val scale = parseScales(listOf("""{ "id": "major", "name": "Major", "intervals": [0, 2, 4, 5, 7, 9, 11] }""")).single()

        assertEquals(TestScales.MAJOR, scale)
    }

    @Test
    fun aBrokenScaleFileIsLeftOut() {
        val scales = parseScales(
            listOf(
                "not json",
                """{ "id": "bad", "name": "Bad", "intervals": [2, 4, 5] }""",
                """{ "id": "minor", "name": "Minor", "intervals": [0, 2, 3, 5, 7, 8, 10] }""",
            ),
        )

        assertEquals(listOf("minor"), scales.map { it.id })
    }

    @Test
    fun everyScaleIsOfferedOnAllTwelveRoots() {
        val choices = ScaleChoice.allIn(listOf(TestScales.MAJOR, TestScales.BLUES))

        assertEquals(24, choices.size)
        assertEquals(choices.size, choices.map { it.id }.distinct().size)
        assertEquals("a_blues", ScaleChoice(TestScales.BLUES, NoteName.A).id)
        assertEquals("a_sharp_major", ScaleChoice(TestScales.MAJOR, NoteName.A_SHARP).id)
    }

    @Test
    fun aRootIsNamedTheWayItsKeyIs() {
        val names = NoteName.entries.map { ScaleChoice(TestScales.MAJOR, it).rootName }

        assertEquals(listOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"), names)
        assertEquals("B♭ Major", ScaleChoice(TestScales.MAJOR, NoteName.A_SHARP).displayName)
    }

    @Test
    fun aChoiceIsFoundByItsIdAndOldSavedIdsStillMatch() {
        val choices = ScaleChoice.allIn(listOf(TestScales.MAJOR, TestScales.NATURAL_MINOR))

        assertEquals(ScaleChoice(TestScales.NATURAL_MINOR, NoteName.A), ScaleChoice.byId("a_minor", choices))
        assertEquals(ScaleChoice(TestScales.MAJOR, NoteName.D), ScaleChoice.byId("d_major", choices))
        assertNull(ScaleChoice.byId("a_blues", choices))
    }

    @Test
    fun theDefaultIsCMajorWhereThereIsOne() {
        val choices = ScaleChoice.allIn(listOf(TestScales.BLUES, TestScales.MAJOR))

        assertEquals("c_major", ScaleChoice.defaultIn(choices)?.id)
        assertEquals("c_blues", ScaleChoice.defaultIn(ScaleChoice.allIn(listOf(TestScales.BLUES)))?.id)
    }

    @Test
    fun aFlatKeyIsSpelledWithFlats() {
        val run = PhraseRecipe(id = "run", name = "", description = "", run = ScaleRun(com.example.saxtrainer.core.music.Beats(0.5)))

        val bFlatMajor = run.resolve(TestScales.MAJOR, Note(NoteName.A_SHARP, 4))

        assertEquals(
            listOf("Bb4", "C5", "D5", "Eb5", "F5", "G5", "A5", "Bb5"),
            bFlatMajor.toneSequence().writtenNotes.take(8).map { it.token },
        )
    }

    @Test
    fun theBundledScalesAreEveryFileWithEveryIdOnce() {
        val ids = ScaleLibrary.all.map { it.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.distinct(), ids)
    }

    /** The Blues 1 melody, as the first blues unit plays it - pinned so a misread degree shows up. */
    @Test
    fun theBlues1MelodyInAReadsAsWritten() {
        val melody = assertNotNull(PatternLibrary.byId("blues_1_melody"))

        val notes = melody.resolve(TestScales.BLUES, Note(NoteName.A, 4)).toneSequence().writtenNotes.map { it.token }

        assertEquals(
            listOf("A4", "C5", "D5", "Eb5", "E5", "E5", "D5", "C5", "A4", "C5", "D5", "C5", "A4"),
            notes,
        )
    }
}

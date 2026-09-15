package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentJsonTest {

    @Test
    fun parsesANoteWithASharp() {
        assertEquals(Note(NoteName.F_SHARP, 4), parseNote("F#4"))
    }

    @Test
    fun parsesANoteWithAFlatAsItsSharpEquivalent() {
        // This app spells pitch classes with sharps internally; a flat in a file still names a
        // real pitch, which is all a step needs - how it is drawn is a display concern, not this
        // parser's.
        assertEquals(Note(NoteName.A_SHARP, 3), parseNote("Bb3"))
    }

    @Test
    fun rejectsSomethingThatIsNotANote() {
        assertNull(parseNote("nope"))
        assertNull(parseNote(""))
    }

    @Test
    fun parsesAPlainAndADottedDuration() {
        val quarter = durationOf("quarter", com.example.saxtrainer.core.music.TimeSignature.FOUR_FOUR)
        val dottedQuarter = durationOf("quarter.", com.example.saxtrainer.core.music.TimeSignature.FOUR_FOUR)

        assertEquals(1.0, quarter!!.value)
        assertEquals(1.5, dottedQuarter!!.value)
    }

    @Test
    fun rejectsAnUnknownDuration() {
        assertNull(durationOf("triplet", com.example.saxtrainer.core.music.TimeSignature.FOUR_FOUR))
    }

    @Test
    fun aPhraseThatFailsToParseIsLeftOutRatherThanCrashing() {
        val phrases = parsePhrases(listOf("not json at all", VALID_PHRASE_JSON))

        assertEquals(1, phrases.size)
        assertEquals("valid", phrases.single().id)
    }

    @Test
    fun aStepWithAnUnreadableDurationDropsTheWholeDocument() {
        val phrases = parsePhrases(listOf(PHRASE_WITH_BAD_DURATION_JSON))

        assertTrue(phrases.isEmpty())
    }

    @Test
    fun aPatternIsReadAsDegreesRatherThanNotes() {
        val patterns = parsePatterns(listOf(VALID_PATTERN_JSON))

        val pattern = patterns.single()
        assertEquals(listOf(1, 2, 3, 5), pattern.bars.single().steps.map { it.degree })
    }

    @Test
    fun theManifestListsFilenamesOrNoneIfItFailsToParse() {
        assertEquals(listOf("a.json", "b.json"), parseManifest("""{"files": ["a.json", "b.json"]}"""))
        assertEquals(emptyList(), parseManifest("not json"))
    }

    @Test
    fun theBundledPhraseLibraryParsesToAtLeastOnePhrase() {
        assertTrue(PhraseLibrary.all.isNotEmpty())
    }

    @Test
    fun theBundledPatternLibraryIncludesABluesPattern() {
        assertTrue(PatternLibrary.all.any { it.id.startsWith("blues") })
    }

    private companion object {
        val VALID_PHRASE_JSON = """
            {
              "id": "valid",
              "name": "Valid",
              "bars": [ { "steps": [ { "note": "C4", "duration": "whole" } ] } ]
            }
        """.trimIndent()

        val PHRASE_WITH_BAD_DURATION_JSON = """
            {
              "id": "bad",
              "name": "Bad",
              "bars": [ { "steps": [ { "note": "C4", "duration": "triplet" } ] } ]
            }
        """.trimIndent()

        val VALID_PATTERN_JSON = """
            {
              "id": "pattern",
              "name": "Pattern",
              "bars": [
                { "steps": [
                  { "degree": 1, "duration": "quarter" },
                  { "degree": 2, "duration": "quarter" },
                  { "degree": 3, "duration": "quarter" },
                  { "degree": 5, "duration": "quarter" }
                ] }
              ]
            }
        """.trimIndent()
    }
}

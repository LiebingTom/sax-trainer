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
        // The pitch of a flat is the one pitch it names, whichever spelling the pitch model uses;
        // how it is written is kept separately - see the written-note tests below.
        assertEquals(Note(NoteName.A_SHARP, 3), parseNote("Bb3"))
    }

    @Test
    fun keepsANoteExactlyAsTheFileWritesIt() {
        assertEquals("Eb4", parseWrittenNote("Eb4")?.token)
        assertEquals("D#4", parseWrittenNote("D#4")?.token)
        assertEquals(parseWrittenNote("Eb4")?.note, parseWrittenNote("D#4")?.note)
    }

    @Test
    fun aFlatOrSharpAcrossTheOctaveSoundsInTheRightOctave() {
        assertEquals(Note(NoteName.B, 3), parseNote("Cb4"))
        assertEquals(Note(NoteName.C, 5), parseNote("B#4"))
        assertEquals(Note(NoteName.E, 4), parseNote("Fb4"))
        assertEquals(Note(NoteName.F, 4), parseNote("E#4"))
    }

    @Test
    fun aPhraseKeepsItsSpellingsWhateverItsStyleSays() {
        val phrase = parsePhrases(
            listOf(
                """
                {"id": "p", "name": "P", "accidentalStyle": "sharps", "bars": [ { "steps": [
                  { "note": "C4", "duration": "quarter" }, { "note": "Eb4", "duration": "quarter" },
                  { "note": "G4", "duration": "quarter" }, { "note": "Bb4", "duration": "quarter" }
                ] } ] }
                """.trimIndent(),
            ),
        ).single()

        assertEquals(
            listOf("C4", "Eb4", "G4", "Bb4"),
            phrase.toneSequence().writtenNotes.map { it.token },
        )
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
    fun theBundledPatternLibraryParsesWithEveryIdOnce() {
        // Empty is allowed: the library starts clean and patterns are added one by one.
        val ids = PatternLibrary.all.map { it.id }
        assertEquals(ids.distinct(), ids)
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

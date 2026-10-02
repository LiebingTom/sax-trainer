package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Spot checks on individual [PatternLibrary] entries transcribed from outside material - worth
 * pinning down once, since a misread note here would otherwise only ever show up by ear.
 */
class PatternLibraryContentTest {

    /** Transcribed from the Woche 1 exercise: an eight-bar melody in D major. */
    @Test
    fun wocheOneWalkMatchesTheSheet() {
        val pattern = assertNotNull(PatternLibrary.byId("woche_1_walk"))

        val resolved = pattern.resolve(Scales.MAJOR, NoteName.D, octaves = listOf(4))

        val expected = listOf(
            "D4", "E4", "F#4", "A4",
            "B4", "A4", "F#4", "E4",
            "D4", "E4", "F#4", "A4",
            "E4", "E4", "F#4", "E4",
            "D4", "A4", "B4", "D4",
            "E4", "A4", "B4", "E4",
            "F#4", "D4",
            "E4", "D4", "B4", "D4",
        )
        assertEquals(
            expected,
            resolved.toneSequence().notes.map { "${it.name.displayName}${it.octave}" },
        )
    }
}

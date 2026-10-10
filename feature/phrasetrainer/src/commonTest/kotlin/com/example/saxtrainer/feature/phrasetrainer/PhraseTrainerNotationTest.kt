package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.designsystem.component.NotationStyle
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.ScaleLibrary
import com.example.saxtrainer.core.settings.exerciseSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Numbers instead of notes: when they can be shown, and how the choice is written down. */
class PhraseTrainerNotationTest {

    private val blues = assertNotNull(ScaleLibrary.byId("blues"))

    private val scalePattern = PhraseTrainerUiState(
        sourceType = PhraseSourceType.SCALE_PATTERN,
        scaleChoiceId = "a_blues",
        patterns = PatternLibrary.all,
        patternId = "scale_up_and_down",
        notation = NotationStyle.NUMBERS,
    )

    @Test
    fun `a scale pattern can be shown as numbers in its key`() {
        assertTrue(scalePattern.canShowNumbers)
        assertEquals(NotationStyle.NUMBERS, scalePattern.effectiveNotation)
        assertEquals("A Blues", scalePattern.keyLabel)
    }

    @Test
    fun `a library phrase falls back to the staff`() {
        val library = scalePattern.copy(
            sourceType = PhraseSourceType.LIBRARY,
            library = PhraseLibrary.all,
            libraryPhraseId = PhraseLibrary.all.first().id,
        )
        assertFalse(library.canShowNumbers)
        assertEquals(NotationStyle.STAFF, library.effectiveNotation)
    }

    @Test
    fun `the choice survives being written down and read back`() {
        val back = PhraseTrainerUiState(patterns = PatternLibrary.all)
            .withSetup(scalePattern.toSetup(), PhraseLibrary, PatternLibrary)
        assertEquals(NotationStyle.NUMBERS, back.notation)
        assertEquals(NotationStyle.NUMBERS, assertNotNull(phraseTrainerPresetOf(scalePattern.toSetup())).notation)
    }

    @Test
    fun `a setup from before numbers existed shows the staff`() {
        val old = exerciseSetup { put("show_seconds", 2.5) }
        assertEquals(NotationStyle.STAFF, PhraseTrainerUiState().withSetup(old, PhraseLibrary, PatternLibrary).notation)
        assertEquals(NotationStyle.STAFF, assertNotNull(phraseTrainerPresetOf(old)).notation)
    }

    @Test
    fun `a preset asking for numbers is applied`() {
        val preset = PhraseTrainerPreset(
            source = PhraseSource.ScalePattern("scale_up_and_down", blues, NoteName.A, setOf(4)),
            notation = NotationStyle.NUMBERS,
        )
        val state = PhraseTrainerUiState(patterns = PatternLibrary.all).withPreset(preset, PhraseLibrary, PatternLibrary)
        assertEquals(NotationStyle.NUMBERS, state.effectiveNotation)
        assertEquals("A Blues", state.keyLabel)
    }
}

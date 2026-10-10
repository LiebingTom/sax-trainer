package com.example.saxtrainer.learning

import com.example.saxtrainer.core.designsystem.component.NotationStyle
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerUiState
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerPresetOf
import com.example.saxtrainer.learning.catalog.units.BLUES_2_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.IMPRO_1_UNIT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Impro 1: the song on the major pentatonic, read as numbers in G and then D. */
class ImproUnitsTest {

    private val unit = assertNotNull(SaxTrainerCurriculum.unitOf(IMPRO_1_UNIT_ID), "no unit $IMPRO_1_UNIT_ID")

    @Test
    fun `impro 1 comes after blues 2`() {
        val ids = SaxTrainerCurriculum.units.map { it.id }
        assertEquals(ids.indexOf(BLUES_2_UNIT_ID) + 1, ids.indexOf(IMPRO_1_UNIT_ID))
        assertEquals("Impro 1", unit.title)
    }

    @Test
    fun `the song is played in G, then in D with its turn kept high`() {
        assertEquals(listOf("impro-1-g-song", "impro-1-d-song"), unit.lessons.map { it.id.value })
        val sources = unit.lessons.map { lesson ->
            val preset = assertNotNull(phraseTrainerPresetOf(lesson.steps.single()))
            assertEquals(NotationStyle.NUMBERS, preset.notation)
            assertEquals(PhraseTrainerUiState.SHOW_THROUGHOUT, preset.showSeconds)
            assertEquals(3, preset.repeats)
            assertIs<PhraseSource.ScalePattern>(preset.source)
        }
        assertEquals(listOf(NoteName.G, NoteName.D), sources.map { it.root })
        assertEquals(listOf("impro_1_song", "impro_1_song_high"), sources.map { it.patternId })
        sources.forEach { assertEquals("major_pentatonic", it.scale.id) }
    }

    @Test
    fun `every note of the song can be played and shown as a number`() {
        unit.lessons.forEach { lesson ->
            val preset = assertNotNull(phraseTrainerPresetOf(lesson.steps.single()))
            val phrase = assertNotNull(preset.source.resolve(PhraseLibrary, PatternLibrary))
            assertTrue(phrase.hasDegrees)
            // Low B flat is the bottom of every saxophone: nothing written under it.
            assertTrue(phrase.toneSequence().notes.all { it.midiNumber >= 58 }, lesson.id.value)
            assertEquals(8, phrase.bars.size)
        }
    }
}

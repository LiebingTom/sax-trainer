package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.learning.catalog.steps.longTonePresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerPresetOf
import com.example.saxtrainer.learning.catalog.units.BLUES_1_UNIT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** What the first blues unit teaches, built from the scale and pattern content files. */
class BluesUnitsTest {

    private val unit = assertNotNull(SaxTrainerCurriculum.unitOf(BLUES_1_UNIT_ID), "no unit $BLUES_1_UNIT_ID")
    private val steps = unit.lessons.flatMap { it.steps }

    private val aBlues = listOf("A4", "C5", "D5", "Eb5", "E5", "G5", "A5")

    @Test
    fun `blues 1 comes after the basics`() {
        assertEquals(BLUES_1_UNIT_ID, SaxTrainerCurriculum.units.last().id)
        assertEquals("Blues 1", unit.title)
    }

    @Test
    fun `its notes are learned like a group's, then the scale and a melody are played`() {
        assertEquals(
            listOf(
                TrainerId.NOTE_QUIZ,
                TrainerId.NOTE_TRAINER,
                TrainerId.LONG_TONES,
                TrainerId.PHRASE_TRAINER,
                TrainerId.PHRASE_TRAINER,
            ),
            unit.lessons.map { it.firstStep.trainerId },
        )
        unit.lessons.forEach { assertEquals(1, it.stepCount, "${it.title} runs more than one practice") }
    }

    @Test
    fun `quiz, trainer and long tones all work on the A blues scale`() {
        assertEquals(aBlues, steps.firstNotNullOf(::noteQuizPresetOf).notes.map { it.token })
        assertEquals(aBlues, steps.firstNotNullOf(::noteTrainerPresetOf).notes.map { it.token })
        assertEquals(aBlues, steps.firstNotNullOf(::longTonePresetOf).notes.map { it.token })
    }

    @Test
    fun `the scale and the melody are the blues on A, octave 4, three times through`() {
        val presets = steps.mapNotNull(::phraseTrainerPresetOf)

        assertEquals(listOf("scale_up_and_down", "blues_1_melody"), presets.map { (it.source as PhraseSource.ScalePattern).patternId })
        presets.forEach { preset ->
            val source = assertIs<PhraseSource.ScalePattern>(preset.source)
            assertEquals("blues", source.scale.id)
            assertEquals(NoteName.A, source.root)
            assertEquals(setOf(4), source.octaves)
            assertEquals(3, preset.repeats)
            assertNotNull(source.resolve(PhraseLibrary, PatternLibrary), "${source.patternId} does not resolve")
        }
    }

    @Test
    fun `the melody stays on the scale's notes`() {
        val melody = steps.mapNotNull(::phraseTrainerPresetOf).last().source.resolve(PhraseLibrary, PatternLibrary)

        val notes = assertNotNull(melody).distinctWrittenNotes().map { it.token }
        assertEquals(notes, notes.filter { it in aBlues })
    }
}

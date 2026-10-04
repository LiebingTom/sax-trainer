package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Lesson
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

    private fun lesson(suffix: String): Lesson =
        assertNotNull(unit.lessons.firstOrNull { it.id.value == "$BLUES_1_UNIT_ID-$suffix" }, "no lesson $suffix")

    private val aBlues = listOf("A4", "C5", "D5", "Eb5", "E5", "G5", "A5")
    private val gBlues = listOf("G4", "Bb4", "C5", "Db5", "D5", "F5", "G5")
    private val dBlues = listOf("D4", "F4", "G4", "Ab4", "A4", "C5", "D5")

    /** C4 to C6, every black key as a sharp and then as a flat. */
    private val halfSteps = listOf(
        "C4", "C#4", "Db4", "D4", "D#4", "Eb4", "E4", "F4", "F#4", "Gb4", "G4", "G#4", "Ab4",
        "A4", "A#4", "Bb4", "B4",
        "C5", "C#5", "Db5", "D5", "D#5", "Eb5", "E5", "F5", "F#5", "Gb5", "G5", "G#5", "Ab5",
        "A5", "A#5", "Bb5", "B5",
        "C6",
    )

    @Test
    fun `blues 1 comes after the basics`() {
        assertEquals(BLUES_1_UNIT_ID, SaxTrainerCurriculum.units.last().id)
        assertEquals("Blues 1", unit.title)
    }

    @Test
    fun `the half steps come first, then the notes, then each key's scale and melody`() {
        assertEquals(
            listOf(
                "half-steps-quiz", "half-steps-play", "play", "long-tones",
                "a-scale", "a-melody", "g-scale", "g-melody", "d-scale", "d-melody",
            ).map { "$BLUES_1_UNIT_ID-$it" },
            unit.lessons.map { it.id.value },
        )
        assertEquals(
            listOf(TrainerId.NOTE_QUIZ, TrainerId.NOTE_TRAINER, TrainerId.NOTE_TRAINER, TrainerId.LONG_TONES) +
                List(6) { TrainerId.PHRASE_TRAINER },
            unit.lessons.map { it.firstStep.trainerId },
        )
        unit.lessons.forEach { assertEquals(1, it.stepCount, "${it.title} runs more than one practice") }
    }

    @Test
    fun `every half step is named and played, each black key both ways`() {
        assertEquals(35, halfSteps.size)
        val quiz = assertNotNull(noteQuizPresetOf(lesson("half-steps-quiz").firstStep))
        val trainer = assertNotNull(noteTrainerPresetOf(lesson("half-steps-play").firstStep))

        assertEquals(halfSteps, quiz.notes.map { it.token })
        assertEquals(halfSteps, trainer.notes.map { it.token })
        assertEquals(1, trainer.rounds)
    }

    @Test
    fun `every note of the three keys is played, each as its scale writes it`() {
        val trainer = assertNotNull(noteTrainerPresetOf(lesson("play").firstStep))

        assertEquals(
            listOf("D4", "F4", "G4", "Ab4", "A4", "Bb4", "C5", "Db5", "D5", "Eb5", "E5", "F5", "G5", "A5"),
            trainer.notes.map { it.token },
        )
        assertEquals((aBlues + gBlues + dBlues).toSet(), trainer.notes.map { it.token }.toSet())
    }

    @Test
    fun `the A blues notes are held`() {
        val longTones = assertNotNull(longTonePresetOf(lesson("long-tones").firstStep))

        assertEquals(aBlues, longTones.notes.map { it.token })
    }

    @Test
    fun `the scale and the melody are played in A, G and D, octave 4, three times through`() {
        val keys = listOf("a" to NoteName.A, "g" to NoteName.G, "d" to NoteName.D)

        keys.forEach { (key, root) ->
            listOf("scale" to "scale_up_and_down", "melody" to "blues_1_melody").forEach { (kind, patternId) ->
                val preset = assertNotNull(phraseTrainerPresetOf(lesson("$key-$kind").firstStep))
                val source = assertIs<PhraseSource.ScalePattern>(preset.source)
                assertEquals(patternId, source.patternId)
                assertEquals("blues", source.scale.id)
                assertEquals(root, source.root)
                assertEquals(setOf(4), source.octaves)
                assertEquals(3, preset.repeats)
                assertNotNull(source.resolve(PhraseLibrary, PatternLibrary), "$key $kind does not resolve")
            }
        }
    }

    @Test
    fun `each scale plays its own key's notes and each melody stays on them`() {
        listOf("a" to aBlues, "g" to gBlues, "d" to dBlues).forEach { (key, scaleNotes) ->
            fun notesOf(kind: String) = assertNotNull(
                phraseTrainerPresetOf(lesson("$key-$kind").firstStep)?.source?.resolve(PhraseLibrary, PatternLibrary),
            ).distinctWrittenNotes().map { it.token }

            assertEquals(scaleNotes, notesOf("scale"))
            val melody = notesOf("melody")
            assertEquals(melody, melody.filter { it in scaleNotes }, "the $key melody leaves its scale")
        }
    }
}

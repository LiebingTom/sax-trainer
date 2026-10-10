package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ScaleDegree
import com.example.saxtrainer.core.music.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A resolved scale pattern keeps each note's degree, so it can be shown as numbers. */
class PhraseDegreesTest {

    private val blues = assertNotNull(ScaleLibrary.byId("blues"))
    private val pentatonic = assertNotNull(ScaleLibrary.byId("major_pentatonic"))
    private val run = assertNotNull(PatternLibrary.byId("scale_up_and_down"))

    private fun Phrase.labels() = bars.flatMap { it.steps }.map { step ->
        step.degree?.let { it.label + "'".repeat(maxOf(it.octave, 0)) + ",".repeat(maxOf(-it.octave, 0)) }
    }

    @Test
    fun `the blues run counts up to the top root and back down`() {
        val phrase = run.resolve(blues, NoteName.A, listOf(4))
        assertEquals(
            listOf("1", "♭3", "4", "♭5", "5", "♭7", "1'", "♭7", "5", "♭5", "4", "♭3", "1", null),
            phrase.labels(),
        )
        assertTrue(phrase.hasDegrees)
    }

    @Test
    fun `a rest has no degree`() {
        val phrase = run.resolve(blues, NoteName.A, listOf(4))
        phrase.bars.flatMap { it.steps }.filter { it.isRest }.forEach { assertNull(it.degree) }
    }

    @Test
    fun `each octave of a two-octave run counts from its own root`() {
        val phrase = run.resolve(blues, NoteName.A, listOf(4, 5))
        val half = phrase.labels().size / 2
        assertEquals(phrase.labels().take(half), phrase.labels().drop(half))
    }

    @Test
    fun `a library phrase has no degrees`() {
        PhraseLibrary.all.forEach { assertFalse(it.hasDegrees, it.id) }
    }

    @Test
    fun `the impro song dips below the root in G and stays above it in D`() {
        val low = assertNotNull(PatternLibrary.byId("impro_1_song")).resolve(pentatonic, NoteName.G, listOf(4))
        val high = assertNotNull(PatternLibrary.byId("impro_1_song_high")).resolve(pentatonic, NoteName.D, listOf(4))
        assertEquals(
            listOf(
                "1", "2", "3", "5", "6", "5", "3", "2", "1", "2", "3", "5", "2", "2", "3", "2",
                "1", "5,", "6,", "1", "2", "5,", "6,", "2", "3", "1", "2", "1", "6,", "1",
            ),
            low.labels(),
        )
        assertEquals(listOf("1", "5", "6", "1", "2", "5", "6", "2"), high.labels().subList(16, 24))
        assertEquals(low.labels().take(16) + low.labels().drop(24), high.labels().take(16) + high.labels().drop(24))
        assertEquals(listOf("D4", "A4", "B4", "D4"), high.bars[4].steps.map { assertNotNull(it.written).token })
        assertEquals(listOf("G4", "D4", "E4", "G4"), low.bars[4].steps.map { assertNotNull(it.written).token })
    }

    @Test
    fun `values joined by a plus are tied into one note`() {
        assertEquals(Beats(2.5), durationOf("half+eighth", TimeSignature.FOUR_FOUR))
        assertEquals(Beats(1.75), durationOf("quarter.+sixteenth", TimeSignature.FOUR_FOUR))
        assertNull(durationOf("half+tenth", TimeSignature.FOUR_FOUR))
    }

    @Test
    fun `the low degrees resolve under the root`() {
        assertEquals(ScaleDegree(5, octave = -1), pentatonic.degreeOf(pentatonic.semitoneOffset(-1)))
        assertEquals(-5, pentatonic.semitoneOffset(-1))
    }
}

package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringKind
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Building a question that has exactly one right answer. */
class QuizQuestionGeneratorTest {

    private val chart = SaxophoneFingerings
    private val octave = listOf(
        Note(NoteName.C, 4),
        Note(NoteName.D, 4),
        Note(NoteName.E, 4),
        Note(NoteName.F, 4),
        Note(NoteName.G, 4),
        Note(NoteName.A, 4),
        Note(NoteName.B, 4),
        Note(NoteName.C, 5),
    )

    private fun generate(
        mode: QuizMode = QuizMode.NOTE_TO_FINGERING,
        pool: List<Note> = octave,
        seed: Int = 1,
        optionCount: Int = 4,
    ) = QuizQuestionGenerator.generate(pool, mode, chart, Random(seed), optionCount)

    @Test
    fun `the right answer is the target's own fingering`() {
        repeat(30) { seed ->
            val question = generate(seed = seed) as NoteToFingering

            assertEquals(
                "seed $seed",
                chart.fingeringsFor(question.note)!!.standard,
                question.options[question.correctIndex],
            )
        }
    }

    @Test
    fun `the right answer is the target note itself`() {
        repeat(30) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, seed = seed) as FingeringToNote

            assertEquals("seed $seed", question.note, question.options[question.correctIndex])
            assertEquals(chart.fingeringsFor(question.note)!!.standard, question.fingering)
        }
    }

    @Test
    fun `no alternate fingering ever appears`() {
        // Standard grips only. It is what makes a question unambiguous without any special
        // handling, and what a player learning their first octave should be asked.
        val notes = listOf(Note(NoteName.A_SHARP, 4), Note(NoteName.F_SHARP, 4)) + octave

        repeat(40) { seed ->
            val question = generate(pool = notes, seed = seed)

            val shown = when (question) {
                is NoteToFingering -> question.options
                is FingeringToNote -> listOf(question.fingering)
            }
            assertTrue(
                "seed $seed showed an alternate",
                shown.all { it.kind == FingeringKind.STANDARD },
            )
        }
    }

    @Test
    fun `no option is shown twice`() {
        repeat(40) { seed ->
            val question = generate(seed = seed) as NoteToFingering

            assertEquals("seed $seed", question.optionCount, question.options.distinct().size)
        }
    }

    @Test
    fun `the options are all notes the player chose`() {
        val pool = octave.take(5)

        repeat(20) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, pool, seed) as FingeringToNote

            assertTrue("seed $seed strayed outside the pool", pool.containsAll(question.options))
        }
    }

    @Test
    fun `the wrong answers are near neighbours, not notes from the other end`() {
        // Distractors far from the target make the question a test of eyesight rather than of
        // recognition, so they are drawn from around the note being asked about.
        val wide = Note.range(Note(NoteName.B, 3), Note(NoteName.F_SHARP, 6))

        repeat(30) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, wide, seed) as FingeringToNote
            val distance = question.options
                .filterNot { it == question.note }
                .maxOf { abs(it.midiNumber - question.note.midiNumber) }

            assertTrue("seed $seed reached $distance semitones away", distance <= 8)
        }
    }

    @Test
    fun `a small pool asks a smaller question rather than repeating an answer`() {
        val question = generate(pool = octave.take(3), optionCount = 4)

        assertEquals(3, question.optionCount)
    }

    @Test
    fun `two notes is still a question`() {
        val question = generate(pool = octave.take(2))

        assertEquals(2, question.optionCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one note is not a question`() {
        generate(pool = octave.take(1))
    }

    @Test
    fun `the right answer is not always in the same place`() {
        val places = (0 until 40).map { generate(seed = it).correctIndex }.toSet()

        assertTrue("the answer only ever appeared at $places", places.size > 1)
    }

    @Test
    fun `the same seed asks the same question`() {
        assertEquals(generate(seed = 5), generate(seed = 5))
    }

    @Test
    fun `two seeds do not ask the same question`() {
        val questions = (0 until 20).map { generate(seed = it) }

        assertTrue("every seed gave the same question", questions.distinct().size > 1)
    }

    @Test
    fun `mixed asks both ways round`() {
        val kinds = (0 until 40)
            .map { generate(QuizMode.MIXED, seed = it) }
            .map { it is NoteToFingering }
            .toSet()

        assertEquals(setOf(true, false), kinds)
    }

    @Test
    fun `a note the chart cannot finger is never asked about`() {
        // Well below the lowest note a tenor saxophone has.
        val pool = octave + Note(NoteName.C, 2)

        repeat(20) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, pool, seed) as FingeringToNote

            assertNotEquals(Note(NoteName.C, 2), question.note)
            assertTrue(question.options.all(chart::supports))
        }
    }
}

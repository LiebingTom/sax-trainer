package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringKind
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    ) = QuizQuestionGenerator.generate(pool, mode, chart, Random(seed))

    @Test
    fun `the right answer is the target's own fingering`() {
        repeat(30) { seed ->
            val question = generate(seed = seed) as NoteToFingering

            assertEquals(
                chart.fingeringsFor(question.note)!!.standard,
                question.options[question.correctIndex],
                "seed $seed",
            )
        }
    }

    @Test
    fun `the right answer is the target note itself`() {
        repeat(30) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, seed = seed) as FingeringToNote

            assertEquals(question.note, question.options[question.correctIndex], "seed $seed")
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
                is NoteAndFingering -> question.fingerings.options
                is NoteToName -> emptyList()
            }
            assertTrue(
                shown.all { it.kind == FingeringKind.STANDARD },
                "seed $seed showed an alternate",
            )
        }
    }

    @Test
    fun `no option is shown twice`() {
        repeat(40) { seed ->
            val question = generate(seed = seed) as NoteToFingering

            assertEquals(question.optionCount, question.options.distinct().size, "seed $seed")
        }
    }

    @Test
    fun `the options are all notes the player chose`() {
        val pool = octave.take(5)

        repeat(20) { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, pool, seed) as FingeringToNote

            assertTrue(pool.containsAll(question.options), "seed $seed strayed outside the pool")
        }
    }

    @Test
    fun `the wrong answers are mostly near neighbours, rarely notes from the other end`() {
        // Distractors far from the target make the question a test of eyesight rather than of
        // recognition, so they are drawn mostly from around the note being asked about. Drawn, not
        // ranked, so this is a tendency over many questions rather than a bound on every one.
        val wide = Note.range(Note(NoteName.B, 3), Note(NoteName.F_SHARP, 6))

        val distances = (0 until 300).flatMap { seed ->
            val question = generate(QuizMode.FINGERING_TO_NOTE, wide, seed) as FingeringToNote
            question.options
                .filterNot { it == question.note }
                .map { abs(it.midiNumber - question.note.midiNumber) }
        }

        val near = distances.count { it <= 4 }.toDouble() / distances.size
        val far = distances.count { it > 12 }.toDouble() / distances.size
        assertTrue(near >= 0.7, "only ${near * 100}% were within four semitones")
        assertTrue(far <= 0.1, "${far * 100}% were more than an octave away")
    }

    @Test
    fun `a question is never about the note asked just before`() {
        listOf(octave.take(2), octave).forEach { pool ->
            pool.forEach { previous ->
                repeat(50) { seed ->
                    val question = QuizQuestionGenerator.generate(
                        pool = pool,
                        mode = QuizMode.NOTE_TO_FINGERING,
                        chart = chart,
                        random = Random(seed),
                        previous = previous,
                    )

                    assertNotEquals(previous, question.note, "seed $seed asked ${previous.label} again")
                }
            }
        }
    }

    @Test
    fun `a small pool asks a smaller question rather than repeating an answer`() {
        val question = generate(pool = octave.take(3))

        assertEquals(3, question.answers.single().optionCount)
    }

    @Test
    fun `two notes is still a question`() {
        val question = generate(pool = octave.take(2))

        assertEquals(2, question.answers.single().optionCount)
    }

    @Test
    fun `one note is not a question`() {
        assertFailsWith<IllegalArgumentException> { generate(pool = octave.take(1)) }
    }

    @Test
    fun `the right answer is not always in the same place`() {
        val places = (0 until 40)
            .map { generate(seed = it).answers.single().correctIndex }
            .toSet()

        assertTrue(places.size > 1, "the answer only ever appeared at $places")
    }

    @Test
    fun `the same seed asks the same question`() {
        assertEquals(generate(seed = 5), generate(seed = 5))
    }

    @Test
    fun `two seeds do not ask the same question`() {
        val questions = (0 until 20).map { generate(seed = it) }

        assertTrue(questions.distinct().size > 1, "every seed gave the same question")
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

    // --- both halves of a note ------------------------------------------------------------------

    @Test
    fun `the combined question asks ten names and four grips`() {
        val question = generate(QuizMode.NOTE_AND_FINGERING, pool = chart.supportedNotes)
            as NoteAndFingering

        val names = question.names
        assertEquals(10, names.optionCount, "ten names is what the mode asks for")
        assertEquals(10, names.options.distinct().size, "and ten different ones")
        assertEquals(question.note, names.options[names.correctIndex])
        assertEquals(1, names.options.count { it == question.note }, "the answer appears once")

        val grips = question.fingerings
        val correctGrip = chart.fingeringsFor(question.note)!!.standard
        assertEquals(4, grips.optionCount, "four grips is a screenful; ten would not be")
        assertEquals(4, grips.options.distinct().size)
        assertEquals(correctGrip, grips.options[grips.correctIndex])
        assertEquals(1, grips.options.count { it == correctGrip }, "the answer appears once")
    }

    @Test
    fun `a pool too small for ten names is not padded out`() {
        // Six notes cannot fill ten names, and can fill four grips: each slot is trimmed to what
        // the pool can supply, on its own.
        val question = generate(QuizMode.NOTE_AND_FINGERING, pool = octave.take(6))
            as NoteAndFingering

        val names = question.names
        assertEquals(6, names.optionCount, "no name was invented to make the number up")
        assertEquals(6, names.options.distinct().size)
        assertEquals(question.note, names.options[names.correctIndex])

        val grips = question.fingerings
        assertEquals(4, grips.optionCount, "the grips were never short of candidates")
        assertEquals(chart.fingeringsFor(question.note)!!.standard, grips.options[grips.correctIndex])
    }
}

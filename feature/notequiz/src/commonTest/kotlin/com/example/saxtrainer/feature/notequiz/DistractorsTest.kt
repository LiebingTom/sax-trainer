package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Choosing the wrong answers: plausible, from the player's own selection, never predictable.
 *
 * The draws are random, so these tests assert properties that hold on every draw, or tendencies over
 * thousands of them - never one particular outcome.
 */
class DistractorsTest {

    private fun note(name: NoteName, octave: Int = 4) = SpelledNote.of(Note(name, octave))

    private val c4 = note(NoteName.C)
    private val cSharp4 = note(NoteName.C_SHARP)
    private val d4 = note(NoteName.D)
    private val dSharp4 = note(NoteName.D_SHARP)
    private val e4 = note(NoteName.E)
    private val f4 = note(NoteName.F)
    private val fSharp4 = note(NoteName.F_SHARP)
    private val g4 = note(NoteName.G)
    private val gSharp4 = note(NoteName.G_SHARP)
    private val a4 = note(NoteName.A)
    private val aSharp4 = note(NoteName.A_SHARP)
    private val b4 = note(NoteName.B)
    private val c5 = note(NoteName.C, 5)

    /** Every natural from C to C: no accidental anywhere, so only distance decides. */
    private val naturals = listOf(c4, d4, e4, f4, g4, a4, b4, c5)

    /** A sparse custom selection, mostly accidentals, with wide gaps between its notes. */
    private val sparse = listOf(c4, cSharp4, dSharp4, fSharp4, gSharp4, aSharp4, b4)

    /** Every distractor drawn over [draws] questions, counted by note. */
    private fun counts(
        target: SpelledNote,
        pool: List<SpelledNote>,
        count: Int = 1,
        draws: Int = 4000,
        seed: Int = 1,
    ): Map<SpelledNote, Int> {
        val random = Random(seed)
        return (1..draws)
            .flatMap { distractorsFor(target, pool, count, random) }
            .groupingBy { it }
            .eachCount()
    }

    private fun Map<SpelledNote, Int>.of(note: SpelledNote) = this[note] ?: 0

    // --- what is drawn at all ----------------------------------------------------------------------

    @Test
    fun `every distractor comes from the pool, is not the target, and appears once`() {
        listOf(naturals, sparse).forEach { pool ->
            pool.forEach { target ->
                repeat(100) { seed ->
                    val drawn = distractorsFor(target, pool, count = 3, random = Random(seed))

                    assertEquals(3, drawn.size, "${target.token}, seed $seed")
                    assertTrue(pool.containsAll(drawn), "seed $seed strayed outside the pool")
                    assertTrue(target !in drawn, "seed $seed offered the answer as a wrong one")
                    assertEquals(drawn.size, drawn.distinct().size, "seed $seed repeated an option")
                }
            }
        }
    }

    @Test
    fun `a pool too small for the count gives what it has`() {
        val pool = listOf(c4, d4, e4)

        assertEquals(setOf(c4, e4), distractorsFor(d4, pool, count = 5, random = Random(1)).toSet())
        assertEquals(emptyList(), distractorsFor(d4, pool, count = 0, random = Random(1)))
    }

    // --- near in pitch -----------------------------------------------------------------------------

    @Test
    fun `nearer notes are drawn more often than distant ones`() {
        val drawn = counts(target = e4, pool = naturals)

        assertTrue(drawn.of(f4) > drawn.of(d4), "one semitone beats two: $drawn")
        assertTrue(drawn.of(d4) > drawn.of(g4), "two beats three: $drawn")
        assertTrue(drawn.of(g4) > drawn.of(c4), "three beats four: $drawn")
        assertTrue(drawn.of(f4) > 10 * drawn.of(c5), "a neighbour far outweighs the octave: $drawn")
    }

    @Test
    fun `in a sparse selection the nearest notes present are favoured`() {
        // F sharp's nearest present neighbours are D sharp and G sharp, whatever lies between them.
        val drawn = counts(target = fSharp4, pool = sparse, count = 3)

        assertTrue(drawn.of(gSharp4) > drawn.of(c4), "G# should beat C: $drawn")
        assertTrue(drawn.of(dSharp4) > drawn.of(c4), "D# should beat C: $drawn")
        assertTrue(drawn.of(gSharp4) > drawn.of(cSharp4), "G# should beat C#: $drawn")
        assertTrue(drawn.of(dSharp4) > drawn.of(cSharp4), "D# should beat C#: $drawn")
    }

    @Test
    fun `wide gaps are measured between the notes that are actually there`() {
        // No note between G4 and either C: the nearer C still wins, and the farther one still comes up.
        val c6 = note(NoteName.C, 6)
        val drawn = counts(target = g4, pool = listOf(c4, g4, c6))

        assertTrue(drawn.of(c4) > 3 * drawn.of(c6), "C4 is ten semitones nearer: $drawn")
        assertTrue(drawn.of(c6) > 0, "a distant note is unlikely, not impossible")
    }

    @Test
    fun `the nearest notes are likeliest, not certain`() {
        // Drawn rather than ranked: the same target does not always come with the same wrong answers.
        val nearestTwo = setOf(d4, f4)
        val sets = (0 until 200).map { seed ->
            distractorsFor(e4, naturals, count = 2, random = Random(seed)).toSet()
        }

        assertTrue(sets.distinct().size > 2, "only ever drew ${sets.distinct()}")
        assertTrue(sets.any { it != nearestTwo }, "always drew the two nearest notes")
        assertTrue(sets.count { it == nearestTwo } > sets.size / 4, "the nearest should still lead")
    }

    @Test
    fun `a note is weighted by its distance and nothing else`() {
        assertTrue(proximityWeight(e4, f4) > proximityWeight(e4, d4))
        assertEquals(proximityWeight(e4, f4), proximityWeight(e4, dSharp4), "one step either way")
        assertTrue(proximityWeight(e4, note(NoteName.E, 5)) > 0.0, "an octave off is still drawable")
    }

    // --- the same kind of note ---------------------------------------------------------------------

    @Test
    fun `a chromatic target always gets a chromatic distractor when there is one`() {
        // A sharp is the only other accidental, and nine semitones away: by distance alone it would
        // rarely be drawn, so every question offering it is the rule at work.
        val pool = naturals.take(7) + cSharp4 + aSharp4

        repeat(300) { seed ->
            val drawn = distractorsFor(cSharp4, pool, count = 3, random = Random(seed))

            assertTrue(aSharp4 in drawn, "seed $seed offered no accidental: $drawn")
        }
    }

    @Test
    fun `the chromatic distractor is still chosen by distance`() {
        val drawn = counts(target = cSharp4, pool = sparse)

        assertTrue(drawn.keys.all { it.note.name.isAccidental }, "one distractor, so it is the rule's: $drawn")
        assertTrue(drawn.of(dSharp4) > drawn.of(fSharp4), "D# is nearer than F#: $drawn")
        assertTrue(drawn.of(fSharp4) > 0, "F# is less likely, not excluded")
    }

    @Test
    fun `a natural target always gets a natural distractor when there is one`() {
        // B is the only other natural, nine semitones away.
        val pool = listOf(cSharp4, d4, dSharp4, fSharp4, gSharp4, aSharp4, b4)

        repeat(300) { seed ->
            val drawn = distractorsFor(d4, pool, count = 2, random = Random(seed))

            assertTrue(b4 in drawn, "seed $seed offered no natural: $drawn")
        }
    }

    @Test
    fun `the kind rule is skipped when the pool has nothing of that kind`() {
        repeat(50) { seed ->
            val forSharp = distractorsFor(cSharp4, listOf(cSharp4, c4, d4, e4), 2, Random(seed))
            assertEquals(2, forSharp.size)
            assertTrue(forSharp.none { it.note.name.isAccidental }, "there was no other accidental to offer")

            val forNatural = distractorsFor(d4, listOf(d4, cSharp4, dSharp4, fSharp4), 3, Random(seed))
            assertEquals(setOf(cSharp4, dSharp4, fSharp4), forNatural.toSet())
        }
    }

    @Test
    fun `the rest are still drawn from both kinds`() {
        // The rule asks for at least one of the target's kind, never for all of them.
        val drawn = counts(target = fSharp4, pool = sparse, count = 3)

        assertTrue(drawn.of(b4) > 0 || drawn.of(c4) > 0, "no natural was ever drawn: $drawn")
    }

    // --- written notes ----------------------------------------------------------------------

    private fun written(token: String) = SpelledNote.parse(token)!!

    @Test
    fun `no other spelling of the target is ever a wrong answer`() {
        val eFlat = written("Eb4")
        val pool = listOf("C4", "D4", "D#4", "Eb4", "E4", "F4").map(::written)

        repeat(50) { seed ->
            val drawn = distractorsFor(eFlat, pool, count = 5, random = Random(seed))
            assertTrue(drawn.none { it.note == eFlat.note }, "seed $seed offered the same key: $drawn")
        }
    }

    @Test
    fun `a pitch the pool holds twice is offered once`() {
        val pool = listOf("C4", "C#4", "Db4", "D4", "E4").map(::written)

        repeat(50) { seed ->
            val drawn = distractorsFor(written("E4"), pool, count = 4, random = Random(seed))
            assertEquals(drawn.size, drawn.map { it.note }.distinct().size, "seed $seed: $drawn")
            assertEquals(3, drawn.size, "three pitches besides the target, however many spellings")
        }
    }

    @Test
    fun `the same kind of note is decided by how it is written`() {
        // F flat sounds as E, a white key, but it is written with a flat: as an answer next to
        // naturals it would give itself away just as a black key would.
        val fFlat = written("Fb4")
        val pool = listOf(fFlat, written("C4"), written("D4"), written("Bb4"), written("G4"))

        repeat(30) { seed ->
            val drawn = distractorsFor(fFlat, pool, count = 1, random = Random(seed))
            assertEquals(listOf(written("Bb4")), drawn, "seed $seed")
        }
    }

    @Test
    fun `neither spelling of a pitch held twice is favoured for coming first`() {
        // C sharp is listed before D flat; over many questions both have to turn up.
        val pool = listOf("C4", "C#4", "Db4", "E4").map(::written)

        val offered = (0 until 200)
            .flatMap { seed -> distractorsFor(written("C4"), pool, count = 2, random = Random(seed)) }
            .filter { it.note == written("C#4").note }
            .toSet()

        assertEquals(setOf(written("C#4"), written("Db4")), offered)
    }
}


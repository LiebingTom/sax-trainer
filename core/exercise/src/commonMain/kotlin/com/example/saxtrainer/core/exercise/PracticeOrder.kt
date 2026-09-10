package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Note
import kotlin.random.Random

/** Whether an exercise follows its notes in pitch order or mixes them up. */
enum class NoteOrder {
    /** Shuffled, so the player has to read the note instead of reciting the scale. */
    RANDOM,

    /** Ascending, useful when first meeting the notes. */
    ORDERED,
}

/**
 * The notes an exercise works through, in the order it works through them.
 *
 * One round plays every selected note exactly once, so nothing is skipped and the length is simply
 * notes times [rounds]. In [NoteOrder.RANDOM] each round is shuffled on its own, and never opens
 * with the note the round before it closed on - which is the difference between mixing the notes up
 * and occasionally asking for the same one twice running. [NoteOrder.ORDERED] is left exactly as it
 * was given.
 *
 * Pure and seeded, so an exercise is reproducible: the order is decided once, up front, and whatever
 * walks it afterwards stays a passive cursor.
 */
fun practiceOrder(
    notes: List<Note>,
    order: NoteOrder,
    rounds: Int = 1,
    random: Random = Random.Default,
): List<Note> {
    require(rounds >= 1) { "an exercise needs at least one round" }

    val pool = notes.distinct()
    if (pool.isEmpty()) return emptyList()

    val ordered = mutableListOf<Note>()
    repeat(rounds) {
        ordered += when (order) {
            NoteOrder.ORDERED -> pool
            NoteOrder.RANDOM -> shuffleAvoidingRepeat(pool, ordered.lastOrNull(), random)
        }
    }
    return ordered
}

/**
 * One note drawn at random from [pool], and never [previous] while there is anything else to draw.
 *
 * The one rule every randomly chosen next note follows, whoever is choosing it: mixing the notes up
 * should not ask for the same note twice running. It is exact rather than retried - the previous
 * note is simply not a candidate - so no generator, however unlucky, can produce a repeat. A pool of
 * one distinct note has nothing else to offer, and that note repeats.
 */
fun randomNoteAvoiding(pool: List<Note>, previous: Note?, random: Random = Random.Default): Note {
    val notes = pool.distinct()
    require(notes.isNotEmpty()) { "there is no note to choose from" }

    val candidates = notes.filterNot { it == previous }.ifEmpty { notes }
    return candidates.random(random)
}

/**
 * A shuffle of the pool that does not open on [previous]: the opening note is drawn by
 * [randomNoteAvoiding], and the rest of the round is shuffled behind it.
 */
private fun shuffleAvoidingRepeat(pool: List<Note>, previous: Note?, random: Random): List<Note> {
    if (previous == null) return pool.shuffled(random)

    val first = randomNoteAvoiding(pool, previous, random)
    return listOf(first) + (pool - first).shuffled(random)
}

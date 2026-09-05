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
 * notes times [rounds]. In [NoteOrder.RANDOM] each round is shuffled on its own, and a shuffle that
 * would repeat the previous note across a round boundary is redrawn - which is the difference
 * between mixing the notes up and occasionally asking for the same one twice running.
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
 * Shuffles the pool, retrying while the first note would repeat the one just played. With a single
 * note in the pool a repeat is unavoidable, and the retries are bounded so an unlucky generator can
 * never spin.
 */
private fun shuffleAvoidingRepeat(pool: List<Note>, previous: Note?, random: Random): List<Note> {
    var shuffled = pool.shuffled(random)
    if (previous == null || pool.size < 2) return shuffled

    var attempts = 0
    while (shuffled.first() == previous && attempts < MAX_RESHUFFLES) {
        shuffled = pool.shuffled(random)
        attempts++
    }
    return shuffled
}

private const val MAX_RESHUFFLES = 8

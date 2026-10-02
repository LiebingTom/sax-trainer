package com.example.saxtrainer.core.exercise

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
 * "The same note" is decided by [pitchOf]. Written notes are passed with their pitch here, so D sharp
 * followed by E flat counts as a repeat - the player would play the same key twice - even though the
 * two are different things to read. When the notes share no pitch the shuffle is exactly the one it
 * always was; when some do, the round is drawn note by note, each time from those that do not repeat
 * the one before, and only where nothing else is left does a pitch follow itself. Every draw takes a
 * note out of the round, so the round always ends.
 *
 * Pure and seeded, so an exercise is reproducible: the order is decided once, up front, and whatever
 * walks it afterwards stays a passive cursor.
 */
fun <T> practiceOrder(
    notes: List<T>,
    order: NoteOrder,
    rounds: Int = 1,
    random: Random = Random.Default,
    pitchOf: (T) -> Any? = { it },
): List<T> {
    require(rounds >= 1) { "an exercise needs at least one round" }

    val pool = notes.distinct()
    if (pool.isEmpty()) return emptyList()

    val ordered = mutableListOf<T>()
    repeat(rounds) {
        ordered += when (order) {
            NoteOrder.ORDERED -> pool
            NoteOrder.RANDOM -> shuffleAvoidingRepeat(pool, ordered.lastOrNull(), random, pitchOf)
        }
    }
    return ordered
}

/**
 * One note drawn at random from [pool], and never one sounding as [previous] while there is anything
 * else to draw.
 *
 * The one rule every randomly chosen next note follows, whoever is choosing it: mixing the notes up
 * should not ask for the same note twice running. It is exact rather than retried - a note with the
 * previous one's [pitchOf] is simply not a candidate - so no generator, however unlucky, can produce
 * a repeat. A pool with only that pitch has nothing else to offer, and it repeats.
 */
fun <T> randomNoteAvoiding(
    pool: List<T>,
    previous: T?,
    random: Random = Random.Default,
    pitchOf: (T) -> Any? = { it },
): T {
    val notes = pool.distinct()
    require(notes.isNotEmpty()) { "there is no note to choose from" }

    val avoided = previous?.let(pitchOf)
    val candidates = if (previous == null) {
        notes
    } else {
        notes.filterNot { pitchOf(it) == avoided }.ifEmpty { notes }
    }
    return candidates.random(random)
}

/**
 * A shuffle of the pool that does not open on [previous]'s pitch, nor - where it can be helped -
 * put one pitch straight after itself inside the round.
 */
private fun <T> shuffleAvoidingRepeat(
    pool: List<T>,
    previous: T?,
    random: Random,
    pitchOf: (T) -> Any?,
): List<T> {
    val sharesPitch = pool.map(pitchOf).distinct().size < pool.size
    if (!sharesPitch) {
        if (previous == null) return pool.shuffled(random)
        val first = randomNoteAvoiding(pool, previous, random, pitchOf)
        return listOf(first) + (pool - first).shuffled(random)
    }

    val remaining = pool.toMutableList()
    val round = mutableListOf<T>()
    var last = previous
    while (remaining.isNotEmpty()) {
        val lastPitch = last?.let(pitchOf)
        val fresh = remaining.filter { last == null || pitchOf(it) != lastPitch }
        // Drawn only from notes that leave the rest of the round still arrangeable without a
        // repeat - a plain random draw could leave D sharp and E flat as the last two. Where no
        // draw can avoid a repeat any more, the pitch simply follows itself.
        val safe = fresh.filter { canFollowWithoutRepeat(remaining - it, pitchOf(it), pitchOf) }
        val next = (safe.ifEmpty { fresh.ifEmpty { remaining } }).random(random)
        round += next
        remaining.remove(next)
        last = next
    }
    return round
}

/**
 * Whether [rest] can be put in some order, after a note of [lastPitch], with no pitch straight
 * after itself: true exactly when its most frequent pitch is not too frequent to be kept apart.
 */
private fun <T> canFollowWithoutRepeat(rest: List<T>, lastPitch: Any?, pitchOf: (T) -> Any?): Boolean {
    if (rest.isEmpty()) return true
    val counts = rest.groupingBy(pitchOf).eachCount()
    val most = counts.values.max()
    val sameAsLast = counts[lastPitch] ?: 0
    // The most frequent pitch needs a different note between each two of its own; the pitch just
    // played cannot open the rest either, so it needs one more of the others.
    return most <= rest.size - most + 1 && sameAsLast <= rest.size - sameAsLast
}

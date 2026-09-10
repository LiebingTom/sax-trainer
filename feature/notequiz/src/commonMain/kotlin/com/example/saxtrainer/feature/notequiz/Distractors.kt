package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.music.Note
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Up to [count] wrong answers for a question about [target], all of them drawn from [pool].
 *
 * Two rules decide which, and only notes actually in the pool are ever considered - nothing is
 * added, and no note between two chosen ones is assumed to exist:
 *
 * 1. **The same kind of note.** If the target is an accidental and the pool holds another
 *    accidental, at least one distractor is an accidental; if the target is natural and the pool
 *    holds another natural, at least one is natural. Otherwise the right answer could be the only
 *    sharp among naturals and give itself away. Where the pool has no such note, the rule is
 *    skipped.
 * 2. **Near in pitch, more often than not.** Every distractor - the one the first rule asks for
 *    included - is drawn with a weight that falls with its distance from the target
 *    ([proximityWeight]). The weights are relative to one another, so a sparse selection favours
 *    whichever of its own notes lie nearest. Drawn rather than ranked: the nearest are the likeliest,
 *    never certain, so the same target does not always come with the same wrong answers.
 *
 * The order of what comes back means nothing; the caller shuffles the target in among it.
 */
internal fun distractorsFor(target: Note, pool: List<Note>, count: Int, random: Random): List<Note> {
    val remaining = pool.distinct().filterNot { it == target }.toMutableList()
    val wanted = count.coerceIn(0, remaining.size)
    val chosen = mutableListOf<Note>()

    fun pick(from: List<Note>) {
        val note = drawNear(target, from, random)
        chosen += note
        remaining -= note
    }

    if (wanted > 0) {
        val sameKind = remaining.filter { it.name.isAccidental == target.name.isAccidental }
        if (sameKind.isNotEmpty()) pick(sameKind)
    }
    while (chosen.size < wanted) pick(remaining)

    return chosen
}

/**
 * How likely [candidate] is to be drawn as a wrong answer for [target], relative to the others.
 *
 * The inverse square of the distance in semitones: a neighbour one step away is four times as
 * likely as one two steps away, and a note an octave off is rarely but not never chosen.
 */
internal fun proximityWeight(target: Note, candidate: Note): Double {
    val semitones = abs(candidate.midiNumber - target.midiNumber).coerceAtLeast(1)
    return 1.0 / semitones.toDouble().pow(PROXIMITY_EXPONENT)
}

/** One of [candidates], drawn with the probability [proximityWeight] gives it. */
private fun drawNear(target: Note, candidates: List<Note>, random: Random): Note {
    val weights = candidates.map { proximityWeight(target, it) }
    var point = random.nextDouble() * weights.sum()
    candidates.forEachIndexed { index, note ->
        point -= weights[index]
        if (point < 0) return note
    }
    // Only reachable through rounding at the very top of the range.
    return candidates.last()
}

private const val PROXIMITY_EXPONENT = 2.0

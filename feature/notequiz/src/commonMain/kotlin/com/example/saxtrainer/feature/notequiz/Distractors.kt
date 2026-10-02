package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.music.Accidental
import com.example.saxtrainer.core.music.SpelledNote
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Up to [count] wrong answers for a question about [target], all of them drawn from [pool].
 *
 * Two rules decide which, and only notes actually in the pool are ever considered - nothing is
 * added, and no note between two chosen ones is assumed to exist.
 *
 * Before either: **no wrong answer sounds like the right one, nor like another wrong one.** A note
 * of the target's pitch is never offered against it - if E flat is asked, D sharp is not wrong, it
 * is the same key - and a pitch the pool holds twice, as D sharp and as E flat, is offered once, in
 * a spelling drawn at random for this question, so neither is favoured for being listed first.
 * Equivalence is pitch, worked out from the notes, never a list of pairs.
 *
 * 1. **The same kind of note.** If the target is written with a sharp or flat and the pool holds
 *    another such note, at least one distractor has one too; if the target is written plain and the
 *    pool holds another plain note, at least one is plain. Otherwise the right answer could be the
 *    only sharp among naturals and give itself away. Where the pool has no such note, the rule is
 *    skipped.
 * 2. **Near in pitch, more often than not.** Every distractor - the one the first rule asks for
 *    included - is drawn with a weight that falls with its distance from the target
 *    ([proximityWeight]). The weights are relative to one another, so a sparse selection favours
 *    whichever of its own notes lie nearest. Drawn rather than ranked: the nearest are the likeliest,
 *    never certain, so the same target does not always come with the same wrong answers.
 *
 * The order of what comes back means nothing; the caller shuffles the target in among it.
 */
internal fun distractorsFor(
    target: SpelledNote,
    pool: List<SpelledNote>,
    count: Int,
    random: Random,
): List<SpelledNote> {
    val remaining = pool.distinct()
        .filterNot { it.note == target.note }
        .groupBy { it.note }
        .values
        // A pitch written only one way draws nothing, so a pool without shared pitches is drawn
        // from exactly as it always was.
        .map { spellings -> spellings.singleOrNull() ?: spellings.random(random) }
        .toMutableList()
    val wanted = count.coerceIn(0, remaining.size)
    val chosen = mutableListOf<SpelledNote>()

    fun pick(from: List<SpelledNote>) {
        val note = drawNear(target, from, random)
        chosen += note
        remaining -= note
    }

    if (wanted > 0) {
        val sameKind = remaining.filter { it.isAltered == target.isAltered }
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
internal fun proximityWeight(target: SpelledNote, candidate: SpelledNote): Double {
    val semitones = abs(candidate.note.midiNumber - target.note.midiNumber).coerceAtLeast(1)
    return 1.0 / semitones.toDouble().pow(PROXIMITY_EXPONENT)
}

/** One of [candidates], drawn with the probability [proximityWeight] gives it. */
private fun drawNear(target: SpelledNote, candidates: List<SpelledNote>, random: Random): SpelledNote {
    val weights = candidates.map { proximityWeight(target, it) }
    var point = random.nextDouble() * weights.sum()
    candidates.forEachIndexed { index, note ->
        point -= weights[index]
        if (point < 0) return note
    }
    // Only reachable through rounding at the very top of the range.
    return candidates.last()
}

/** Written with a sharp or a flat - what a reader sees, which is what could give an answer away. */
private val SpelledNote.isAltered: Boolean get() = accidental != Accidental.NONE

private const val PROXIMITY_EXPONENT = 2.0

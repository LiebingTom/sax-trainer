package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.music.Note
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Extra wrong notes to pad the option grid out to [total] tiles, drawn near the pitches already in
 * [correct] - the same "close by, more often than not" weighting the note quiz's own distractors
 * use (see `feature/notequiz/Distractors.kt`), so a decoy is a note that could plausibly be
 * mistaken for a right one, not a random pitch from across the horn.
 *
 * A decoy is never one of [correct]'s own pitches, or a decoy already drawn: every tile in the grid
 * reads as its own note. Where the neighbourhood cannot supply enough distinct pitches, fewer than
 * [total] come back - the caller shows however many there are, never pads with something implausible.
 */
internal fun decoysFor(correct: List<Note>, total: Int, random: Random): List<Note> {
    if (correct.isEmpty()) return emptyList()
    val wanted = (total - correct.size).coerceAtLeast(0)
    if (wanted == 0) return emptyList()

    val used = correct.toMutableSet()
    val low = correct.minOf { it.midiNumber } - DECOY_RANGE_SEMITONES
    val high = correct.maxOf { it.midiNumber } + DECOY_RANGE_SEMITONES
    val neighbourhood = (low..high).map(Note::fromMidi)

    val decoys = mutableListOf<Note>()
    while (decoys.size < wanted) {
        val candidates = neighbourhood.filterNot { it in used }
        if (candidates.isEmpty()) break
        val anchor = correct.random(random)
        val note = drawNear(anchor, candidates, random)
        decoys += note
        used += note
    }
    return decoys
}

/** One of [candidates], drawn with a probability that falls with its distance from [target]. */
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

/** The inverse square of the distance in semitones: a neighbour is favoured, never guaranteed. */
private fun proximityWeight(target: Note, candidate: Note): Double {
    val semitones = abs(candidate.midiNumber - target.midiNumber).coerceAtLeast(1)
    return 1.0 / semitones.toDouble().pow(PROXIMITY_EXPONENT)
}

private const val PROXIMITY_EXPONENT = 2.0

/** How far past the correct notes' own range a decoy may be drawn from. */
private const val DECOY_RANGE_SEMITONES = 12

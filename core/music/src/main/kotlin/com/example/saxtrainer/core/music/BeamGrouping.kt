package com.example.saxtrainer.core.music

import kotlin.math.floor

/**
 * How this app's notation decides which short notes are joined by a beam.
 *
 * **This is one rule, not the rule.** Real engraving groups by the metre's beat structure, which
 * differs between 4/4, 6/8 and 3/4, and permits beams across a beat where the music asks for them.
 * What is implemented here is the plain case: within a bar, consecutive flagged notes that begin in
 * the same beat are beamed together, which is correct and readable for the simple 4/4 exercises this
 * app ships. Compound metres and cross-beat groups need a real grouping table, and this function is
 * where that would go - callers only ever ask for the groups, never work them out themselves.
 *
 * A group of one is not a group: a lone eighth keeps its flag.
 *
 * Returned ranges are indices into [ToneSequence.steps], in order, and never overlap.
 */
fun ToneSequence.beamGroups(): List<IntRange> {
    val groups = mutableListOf<IntRange>()
    var start: Int? = null
    var groupBeat = -1.0

    steps.indices.forEach { index ->
        val beat = beatIndexOf(index)
        val beamable = beamCountOf(index) > 0

        val continues = beamable && start != null && beat == groupBeat
        if (!continues) {
            start?.let { first -> if (index - 1 > first) groups += first..index - 1 }
            start = if (beamable) index else null
            groupBeat = if (beamable) beat else -1.0
        }
    }
    start?.let { first -> if (steps.lastIndex > first) groups += first..steps.lastIndex }

    return groups
}

/** How many beams or flags the step at [index] carries; zero for a quarter note or longer. */
fun ToneSequence.beamCountOf(index: Int): Int =
    notatedDurationOf(index)?.value?.beamCount ?: 0

/**
 * How the step at [index] is written, or null when no single note head says it.
 *
 * Null is a real answer: a five-beat long tone is a tie in engraved music, and this app does not
 * write ties. Whoever draws it decides what to do with that.
 */
fun ToneSequence.notatedDurationOf(index: Int): NotatedDuration? =
    NotatedDuration.of(steps[index].duration, timeSignature.beatUnit)

/** Which beat of the piece the step at [index] begins in, counted from zero. */
private fun ToneSequence.beatIndexOf(index: Int): Double = floor(startBeatOf(index).value)

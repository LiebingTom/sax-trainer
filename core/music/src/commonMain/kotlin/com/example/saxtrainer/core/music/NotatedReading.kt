package com.example.saxtrainer.core.music

import kotlin.math.floor

/**
 * One note as it is written down: a single head on the page.
 *
 * A sounding note and a written note are not the same thing. A tone held for five beats in four-four
 * is one note to the player and two to the reader - a whole note tied over the bar line to a quarter
 * - and this is the reader's half of that. Several of these can belong to one [ToneStep], which is
 * what [stepIndex] says and what [tiedToNext] joins back together.
 */
data class NotatedNote(
    /** Which step of the sequence this is part of. Several written notes can share one step. */
    val stepIndex: Int,
    /** The pitch written here, or null where this is a rest. */
    val note: Note?,
    /** Where this written note begins, in beats from the start of the sequence. */
    val startBeat: Beats,
    /** How long this written note is, which is its own value's length rather than the step's. */
    val duration: Beats,
    /** How it is written, or null where even this piece has no single head of its own. */
    val notated: NotatedDuration?,
    /**
     * True when the note after it continues the same sounding note, so a tie joins them.
     *
     * Never true of a rest: two rests either side of a bar line are two silences, and tying them
     * would say a silence is being held, which is not something notation says.
     */
    val tiedToNext: Boolean,
    /** True when it continues the note before it: the far end of a tie. */
    val continuesPrevious: Boolean,
)

/**
 * The sequence as it would be written down.
 *
 * Timing stays in beats and stays the source of truth; this is a reading of it, derived on demand
 * and stored nowhere. Two things happen here, in this order, and both are what an engraver does:
 *
 * Rests are read the same way as notes: cut at a bar line, written with the values that exist, and
 * told apart only by having no pitch. What they do not get is a tie, since a silence is not held
 * over anything.
 *
 * 1. **A note is cut at every bar line it crosses.** A bar line is a fact about the metre, not a
 *    thing that appears only when it is convenient, so a five-beat tone becomes four beats and one
 *    beat rather than one shape that quietly swallows the bar.
 * 2. **What is left inside a bar is written with the values that exist.** Longest first, so three
 *    beats of four-four is a dotted half rather than a half tied to a quarter. Where a remainder is
 *    shorter than the shortest value this app draws, it is passed through as it is and comes out
 *    with a null [NotatedNote.notated] - honest about being unwritable rather than rounded away.
 *
 * Everything is read off [ToneSequence.timeSignature]; nothing here knows four-four from six-eight.
 * Longest-first is a plain rule rather than the rule - real engraving groups a bar by its beats, so
 * that six-eight reads as two dotted quarters rather than one dotted half - and this is where that
 * would go, which is why callers ask for the reading instead of splitting durations themselves.
 */
fun ToneSequence.notatedReading(): List<NotatedNote> {
    val beatsPerBar = timeSignature.beatsPerBar.toDouble()
    val values = writableBeats(timeSignature.beatUnit)
    val reading = mutableListOf<NotatedNote>()
    var sequenceBeat = 0.0

    steps.forEachIndexed { stepIndex, step ->
        val lengths = mutableListOf<Pair<Double, Double>>()
        var at = sequenceBeat
        var left = step.duration.value

        while (left > TOLERANCE) {
            // The bar line the note runs into next, and how much of the bar is left before it.
            val barEnd = (floor(at / beatsPerBar + TOLERANCE) + 1) * beatsPerBar
            val inThisBar = minOf(left, barEnd - at)
            for (length in writtenAs(inThisBar, values)) {
                lengths += at to length
                at += length
            }
            left -= inThisBar
        }

        lengths.forEachIndexed { piece, (start, length) ->
            reading += NotatedNote(
                stepIndex = stepIndex,
                note = step.note,
                startBeat = Beats(start),
                duration = Beats(length),
                notated = NotatedDuration.of(Beats(length), timeSignature.beatUnit),
                tiedToNext = !step.isRest && piece < lengths.lastIndex,
                continuesPrevious = !step.isRest && piece > 0,
            )
        }
        sequenceBeat += step.duration.value
    }

    return reading
}

/** Every written value, longest first, in the beats of a bar counted in [beatUnit]. */
private fun writableBeats(beatUnit: Int): List<Double> =
    NoteValue.entries
        .flatMap { value -> (0..NotatedDuration.MAX_DOTS).map { NotatedDuration(value, it) } }
        .map { it.beats(beatUnit).value }
        .sortedDescending()

/** [beats] as a run of written values, longest first. */
private fun writtenAs(beats: Double, values: List<Double>): List<Double> {
    val lengths = mutableListOf<Double>()
    var left = beats
    while (left > TOLERANCE) {
        val fits = values.firstOrNull { it <= left + TOLERANCE }
        if (fits == null) {
            // Shorter than the shortest value there is. Kept whole rather than dropped or rounded:
            // it comes back with no written form, which is a true answer about it.
            lengths += left
            break
        }
        lengths += fits
        left -= fits
    }
    return lengths
}

/** Beats are doubles; a bar line should not be missed because of the last bit of one. */
private const val TOLERANCE = 1e-9

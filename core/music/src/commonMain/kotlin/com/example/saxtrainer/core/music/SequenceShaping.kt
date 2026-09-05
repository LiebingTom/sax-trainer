package com.example.saxtrainer.core.music

/**
 * Ways of making more practice out of one piece of material.
 *
 * Both operations produce an ordinary [ToneSequence], so nothing downstream - the runner, the
 * notation, the timing check - needs to know that a sequence was shaped rather than written out.
 */

/**
 * The sequence, then the same notes back to where they started.
 *
 * A scale played up and then down: the turning point is not played twice, and neither is the note it
 * began on repeated at the join. Each returning note keeps the length it had on the way up, which is
 * how a scale is normally practised.
 *
 * Not melodic inversion, which turns intervals upside down and is a different thing entirely; this
 * is a retrograde return, and the trainer offers it as "add inverse" because that is what a player
 * asking for it means.
 */
fun ToneSequence.andBackDown(): ToneSequence {
    if (steps.size < 2) return this
    return copy(steps = steps + steps.dropLast(1).reversed())
}

/**
 * The whole sequence, [times] times over.
 *
 * Written out rather than marked with a repeat sign: the exercise then has one honest list of notes,
 * so a beat position, a bar line and a timing check all mean the same thing in the fifth pass as in
 * the first. Drawing a repeat sign instead would be a notation improvement, not a model one.
 */
fun ToneSequence.repeated(times: Int): ToneSequence {
    require(times >= 1) { "a sequence is played at least once, not $times times" }
    if (times == 1) return this
    return copy(steps = List(times) { steps }.flatten())
}

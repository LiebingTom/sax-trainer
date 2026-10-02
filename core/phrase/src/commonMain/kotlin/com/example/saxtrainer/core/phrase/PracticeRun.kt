package com.example.saxtrainer.core.phrase

import kotlin.random.Random

/**
 * The units one run of a phrase or a pattern actually walks: [units] repeated [repeats] times.
 *
 * Not [mixed]: each pass is [units] in its own order, one whole pass after another - asking for a
 * phrase three times over gets it three times, start to finish, not some blend of the three.
 * [mixed]: every repeat's units are drawn from one shuffled pool instead, so a unit from the second
 * pass can come before one from the first - variety across repeats, not only within one. Either way
 * the pool is exactly [repeats] copies of [units], so nothing is asked for more or less often than
 * anything else.
 */
fun List<PracticeUnit>.repeated(repeats: Int, mixed: Boolean, random: Random = Random.Default): List<PracticeUnit> {
    require(repeats >= 1) { "repeats must be at least 1" }
    val pool = List(repeats) { this }.flatten()
    return if (mixed) pool.shuffled(random) else pool
}

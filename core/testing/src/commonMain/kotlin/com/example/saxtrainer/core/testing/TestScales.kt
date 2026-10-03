package com.example.saxtrainer.core.testing

import com.example.saxtrainer.core.music.Scale

/**
 * Scales for tests, so they do not depend on what the bundled scale files happen to hold - the same
 * three the app ships with today.
 */
object TestScales {
    val MAJOR = Scale(id = "major", name = "Major", intervals = listOf(0, 2, 4, 5, 7, 9, 11))

    val NATURAL_MINOR = Scale(id = "minor", name = "Minor", intervals = listOf(0, 2, 3, 5, 7, 8, 10))

    /** Root, flat three, four, flat five, five, flat seven - the flat five on the fifth's letter. */
    val BLUES = Scale(
        id = "blues",
        name = "Blues",
        intervals = listOf(0, 3, 5, 6, 7, 10),
        letterSteps = listOf(0, 2, 3, 4, 4, 6),
    )
}

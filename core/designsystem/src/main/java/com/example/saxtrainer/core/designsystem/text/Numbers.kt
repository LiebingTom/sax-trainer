package com.example.saxtrainer.core.designsystem.text

import kotlin.math.abs
import kotlin.math.floor

/**
 * Number formatting for anything the app puts on screen.
 *
 * Written by hand rather than with `String.format`, which is a JVM-only extension. It also fixes a
 * quiet bug in what it replaces: `"%.1f".format(x)` follows the platform's default locale, so a
 * reading that should say `440.0 Hz` says `440,0 Hz` on a German phone while every other number in
 * the same sentence is spelled the other way. Readings here always use a dot.
 */
fun Double.toFixed(digits: Int): String {
    require(digits >= 0) { "digits must not be negative, was $digits" }
    if (isNaN()) return "-"
    if (isInfinite()) return if (this > 0) "∞" else "-∞"

    var scale = 1L
    repeat(digits) { scale *= 10 }
    // Rounded on the magnitude, so a tie goes away from zero the way a reader expects.
    // `round` will not do: it rounds a tie to the even neighbour, which would show 0.125 as 0.12.
    val scaled = floor(abs(this) * scale + 0.5).toLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    val whole = scaled / scale
    if (digits == 0) return "$sign$whole"
    return "$sign$whole.${(scaled % scale).toString().padStart(digits, '0')}"
}

fun Float.toFixed(digits: Int): String = toDouble().toFixed(digits)

/** A duration as `m:ss`, for a total that is already whole seconds. */
fun minutesAndSeconds(totalSeconds: Int): String {
    val seconds = totalSeconds % 60
    return "${totalSeconds / 60}:${seconds.toString().padStart(2, '0')}"
}

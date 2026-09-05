package com.example.saxtrainer.core.exercise

import kotlin.time.TimeSource

/**
 * The reading every clock in the app takes its elapsed time from.
 *
 * Deliberately a plain `() -> Long` rather than a type: each clock already accepts one as an
 * injectable constructor parameter so tests can run on virtual time, and this is only the default
 * they fall back to.
 *
 * The origin is arbitrary - the value means nothing on its own, only the difference between two
 * readings does. What matters is that it never goes backwards, which is why it comes from
 * [TimeSource.Monotonic] rather than from a wall clock.
 */
val monotonicNanos: () -> Long = { origin.elapsedNow().inWholeNanoseconds }

private val origin = TimeSource.Monotonic.markNow()

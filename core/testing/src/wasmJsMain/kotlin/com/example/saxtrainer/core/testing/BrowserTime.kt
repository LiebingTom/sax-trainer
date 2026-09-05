package com.example.saxtrainer.core.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Waiting for something a browser has to run in its own time.
 *
 * `ComposeUiTest.waitUntil` is a synchronous loop over composition frames, so while it runs the
 * browser's one thread is held and nothing scheduled with `setTimeout` can fire. That is every
 * `delay` in the app: the exercise clocks, the count-in, the microphone flow. A `waitUntil` on a
 * running trainer therefore waits for something that cannot happen.
 *
 * A v2 test body is a suspending function, so it can suspend on a real dispatcher instead and give
 * the thread back. This is that, as a loop: yield, let composition settle, look again.
 *
 * Use it whenever the thing being waited for is the app's own clock. [ComposeUiTest.waitUntil] is
 * still right - and faster - when the test itself caused the change.
 */
@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.awaitInRealTime(
    what: String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    condition: () -> Boolean,
) {
    val deadline = TimeSource.Monotonic.markNow() + timeoutMillis.milliseconds
    while (!condition()) {
        check(deadline.hasNotPassedNow()) { "$what did not happen within $timeoutMillis ms" }
        letTheBrowserRun()
    }
}

/** Hands the thread back for one slice, then lets composition catch up with what changed. */
@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.letTheBrowserRun(millis: Long = SLICE_MS) {
    withContext(Dispatchers.Default) { delay(millis) }
    awaitIdle()
}

/**
 * Generous: these waits are on the browser's clock, not a virtual one, and a machine running the
 * whole suite is not a quick one. It is a deadline against a hang, not a statement about speed.
 */
private const val DEFAULT_TIMEOUT_MS = 30_000L

/** Long enough for a few frames of a 50 ms exercise tick, short enough to feel like polling. */
private const val SLICE_MS = 30L

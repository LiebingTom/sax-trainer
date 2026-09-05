package com.example.saxtrainer.core.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.test.TestResult

/**
 * A browser test for one screen, torn down afterwards.
 *
 * Every case in a module shares one browser page, so a screen left running is still running during
 * the next one - still ticking its exercise clock, still collecting from its microphone. Ending the
 * composition does not stop that, because the ViewModel store outlives it. This gives the body a
 * host and disposes it however the body ends.
 */
@OptIn(ExperimentalTestApi::class)
fun runScreenTest(body: suspend ComposeUiTest.(ScreenHost) -> Unit): TestResult = runComposeUiTest {
    val host = ScreenHost()
    try {
        body(host)
    } finally {
        host.dispose()
    }
}

/**
 * What every screen needs around it in a browser test: a ViewModel store and a lifecycle.
 *
 * It is what an `Activity` provides on Android and what `ComposeViewport` provides on the web. A
 * test that wants to background a screen drives [registry] directly.
 */
class ScreenHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()

    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry

    /** Backgrounds the screen and brings it back, which is how a route is asked to stop. */
    fun leaveAndReturn() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Tears the screen down for good, cancelling every ViewModel scope it started.
     *
     * Ending a composition does not do this: the store outlives it, exactly as it does when an
     * Activity is recreated. Every test in a module shares one browser page, so a ViewModel left
     * running is still collecting during the next case - which is harmless over a fake microphone
     * and decidedly not over a real one, where it also holds an open `AudioContext` and a live
     * `getUserMedia` stream.
     */
    fun dispose() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/** Whether anything on screen carries this text. The condition most waits are written against. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** The same question for something that is drawn rather than written, such as an icon button. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.labels(contentDescription: String): Boolean =
    onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isNotEmpty()

/**
 * A list only composes what is visible, so an item further down it does not exist yet and cannot
 * simply be scrolled to. This scrolls the one scrollable on screen until it does.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.scrolledTo(text: String): SemanticsNodeInteraction {
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
    return onNodeWithText(text)
}

/**
 * How long to wait for something the test itself caused.
 *
 * Generous, because these waits are on the browser's clock rather than on a virtual one and the
 * machine running the whole suite is not a quick one. Waiting on the *app's* clock is
 * [awaitInRealTime] instead, which has a deadline of its own.
 */
const val BROWSER_TIMEOUT_MS = 10_000L

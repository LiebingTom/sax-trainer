@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.MetronomeEngineState
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The metronome screen, running in a browser over a stand-in click.
 *
 * The click itself is `WebMetronome`'s business and is tested against a real `AudioContext` in
 * `:core:audio`. What is checked here is the half this module owns: that the shared ViewModel, the
 * shared screen and the lifecycle behave on wasmJs as they do on Android - intent reaches the
 * engine, the engine's state reaches the screen, and leaving the screen stops the click.
 */
class MetronomeOnWasmTest {

    @Test
    fun the_screen_is_drawn_from_shared_code() = runComposeUiTest {
        setContent { Metronome(MetronomeHost(), FakeMetronome()) }

        onNodeWithText("Metronome").assertIsDisplayed()
        onNodeWithText("80").assertIsDisplayed()
        onNodeWithText("BPM  -  4/4").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("Tap tempo").assertIsDisplayed()
    }

    @Test
    fun nudging_the_tempo_goes_through_the_engine_and_comes_back_to_the_screen() =
        runComposeUiTest {
            val engine = FakeMetronome()
            setContent { Metronome(MetronomeHost(), engine) }

            onNodeWithText("+5").performClick()
            waitUntil("the new tempo", TIMEOUT) { shows("85") }

            onNodeWithText("-1").performClick()
            waitUntil("the nudged tempo", TIMEOUT) { shows("84") }
            assertEquals(84, engine.state.value.bpm, "the engine is what holds the tempo")
        }

    @Test
    fun a_beat_the_engine_plays_reaches_the_indicator() = runComposeUiTest {
        val engine = FakeMetronome()
        setContent { Metronome(MetronomeHost(), engine) }

        onNodeWithText("Start").performClick()
        waitUntil("the running screen", TIMEOUT) { shows("Stop") }

        engine.play(beatInBar = 2)

        waitUntil("the third beat of the bar", TIMEOUT) { labels("Beat 3 of 4") }
    }

    @Test
    fun going_to_the_background_stops_the_click() = runComposeUiTest {
        val host = MetronomeHost()
        val engine = FakeMetronome()
        setContent { Metronome(host, engine) }

        onNodeWithText("Start").performClick()
        waitUntil("the running screen", TIMEOUT) { shows("Stop") }

        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertTrue(!engine.state.value.isRunning, "the click kept going with the screen gone")
        // Coming back is part of the check: below STARTED the screen is not collecting state, so
        // the stopped engine only shows on return - the same on both platforms.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        waitUntil("the stopped screen", TIMEOUT) { shows("Start") }
    }
}

/** A click with no sound: it holds the state a real engine would publish, and nothing else. */
private class FakeMetronome : Metronome {
    private val _state = MutableStateFlow(MetronomeEngineState())
    override val state: StateFlow<MetronomeEngineState> = _state

    var released = false
        private set

    override fun setBpm(bpm: Int) {
        _state.value = _state.value.copy(bpm = bpm)
    }

    override fun start() {
        _state.value = _state.value.copy(isRunning = true)
    }

    override fun stop() {
        _state.value = _state.value.copy(isRunning = false)
    }

    override fun release() {
        released = true
        stop()
    }

    /** What a real engine does once a click has actually been heard. */
    fun play(beatInBar: Int) {
        _state.value = _state.value.copy(
            beatInBar = beatInBar,
            beatCounter = _state.value.beatCounter + 1,
        )
    }
}

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

private fun ComposeUiTest.labels(contentDescription: String): Boolean =
    onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isNotEmpty()

/** Everything [MetronomeRoute] expects of a host: a ViewModel store and a lifecycle. */
private class MetronomeHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun Metronome(host: MetronomeHost, engine: Metronome) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            MetronomeRoute(newMetronome = { engine }, onBack = {})
        }
    }
}

/** Generous: these waits are on the browser's clock, not a virtual one. */
private const val TIMEOUT = 10_000L

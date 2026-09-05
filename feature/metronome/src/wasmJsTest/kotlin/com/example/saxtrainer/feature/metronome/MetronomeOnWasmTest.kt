@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.MetronomeEngineState
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.labels
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
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
    fun the_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Metronome(host, FakeMetronome()) }

        onNodeWithText("Metronome").assertIsDisplayed()
        onNodeWithText("80").assertIsDisplayed()
        onNodeWithText("BPM  -  4/4").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("Tap tempo").assertIsDisplayed()
    }

    @Test
    fun nudging_the_tempo_goes_through_the_engine_and_comes_back_to_the_screen() =
        runScreenTest { host ->
            val engine = FakeMetronome()
            setContent { Metronome(host, engine) }

            onNodeWithText("+5").performClick()
            waitUntil("the new tempo", BROWSER_TIMEOUT_MS) { shows("85") }

            onNodeWithText("-1").performClick()
            waitUntil("the nudged tempo", BROWSER_TIMEOUT_MS) { shows("84") }
            assertEquals(84, engine.state.value.bpm, "the engine is what holds the tempo")
        }

    @Test
    fun a_beat_the_engine_plays_reaches_the_indicator() = runScreenTest { host ->
        val engine = FakeMetronome()
        setContent { Metronome(host, engine) }

        onNodeWithText("Start").performClick()
        waitUntil("the running screen", BROWSER_TIMEOUT_MS) { shows("Stop") }

        engine.play(beatInBar = 2)

        waitUntil("the third beat of the bar", BROWSER_TIMEOUT_MS) { labels("Beat 3 of 4") }
    }

    @Test
    fun going_to_the_background_stops_the_click() = runScreenTest { host ->
        val engine = FakeMetronome()
        setContent { Metronome(host, engine) }

        onNodeWithText("Start").performClick()
        waitUntil("the running screen", BROWSER_TIMEOUT_MS) { shows("Stop") }

        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertTrue(!engine.state.value.isRunning, "the click kept going with the screen gone")
        // Coming back is part of the check: below STARTED the screen is not collecting state, so
        // the stopped engine only shows on return - the same on both platforms.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        waitUntil("the stopped screen", BROWSER_TIMEOUT_MS) { shows("Start") }
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

@Composable
private fun Metronome(host: ScreenHost, engine: Metronome) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            MetronomeRoute(newMetronome = { engine }, onBack = {})
        }
    }
}

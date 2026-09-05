@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.sequence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.awaitInRealTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test

/**
 * The sequence trainer, running in a browser.
 *
 * A sequence is the one exercise that keeps time by itself: it counts in, walks a written line beat
 * by beat and ends when the line does, with the microphone listening throughout. That whole run is
 * checked against a virtual clock on both targets by `SequenceViewModelTest`; what is checked here
 * is that it also happens on the browser's real clock, in Wasm, at the pace the tempo asks for.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class SequenceOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runComposeUiTest {
        setContent { Sequence(SequenceHost()) }

        onNodeWithText("Sequence Trainer").assertIsDisplayed()
        onNodeWithText("Add inverse").performScrollTo().assertIsDisplayed()
        onNodeWithText("Check pitch").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun a_preset_counts_in_walks_the_line_and_ends_where_the_line_does() = runComposeUiTest {
        setContent { Sequence(SequenceHost(), preset = quickScale()) }

        // Counted out on the clock, because this preset asked for no click.
        awaitInRealTime("the count-in") { shows("Counting you in") }
        awaitInRealTime("the line") { shows("Stop") }

        // Nothing further is done to it: the exercise ends because the written line ran out.
        awaitInRealTime("the end of the line") { shows("Sequence complete") }
    }

    @Test
    fun the_tempo_the_preset_asked_for_is_the_one_on_screen() = runComposeUiTest {
        setContent { Sequence(SequenceHost(), preset = quickScale()) }

        awaitInRealTime("the running exercise") { shows("$FAST_BPM BPM") }
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runComposeUiTest {
        val host = SequenceHost()
        setContent { Sequence(host, preset = quickScale()) }

        awaitInRealTime("the running exercise") { shows("Stop") }

        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // Below STARTED the screen stops collecting, so a stopped exercise only shows on return -
        // the same on both platforms.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/**
 * One scale, once through, fast, and silently counted in.
 *
 * The click is off because a real one is the browser's `AudioContext`, which `:core:audio` tests on
 * its own; without it the same count is kept on the clock, which is the path this trainer shares
 * with every other.
 */
private fun quickScale() = SequencePreset(
    sequenceId = SequenceLibrary.cMajorUp.id,
    tempo = Tempo(FAST_BPM),
    repeats = 1,
    metronome = false,
    checkPitch = true,
)

/** Fast enough that a whole scale is a few seconds of a browser test. */
private const val FAST_BPM = 240

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Everything [SequenceRoute] expects of a host: a ViewModel store and a lifecycle. */
private class SequenceHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun Sequence(host: SequenceHost, preset: SequencePreset? = null) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            SequenceRoute(
                settingsRepository = FakeSettingsRepository(),
                audioCapture = FakeMicrophone(frameDurationMs = 10),
                newMetronome = { SilentMetronome() },
                onBack = {},
                preset = preset,
            )
        }
    }
}

/** A click with no sound, for the preset that does not ask for one. */
private class SilentMetronome : Metronome {
    private val _state = MutableStateFlow(MetronomeEngineState())
    override val state: StateFlow<MetronomeEngineState> = _state

    override fun setBpm(bpm: Int) {
        _state.value = _state.value.copy(bpm = bpm)
    }

    override fun start() {
        _state.value = _state.value.copy(isRunning = true)
    }

    override fun stop() {
        _state.value = _state.value.copy(isRunning = false)
    }

    override fun release() = stop()
}

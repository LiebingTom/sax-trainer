@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.longtones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.awaitInRealTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test

/**
 * Long tones, running in a browser.
 *
 * This is the heaviest trainer per frame - a spectrum and a pitch estimate on every hop, redrawn
 * live - and the browser gives it one thread to do that on. `LongToneViewModelTest` checks what it
 * measures against a virtual clock on both targets; what is checked here is that the whole chain
 * still keeps up on wasmJs in real time: frames from the microphone, through the gate and the
 * analysis, onto a screen that is being redrawn while they arrive.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class LongToneOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runComposeUiTest {
        setContent { LongTones(LongToneHost()) }

        onNodeWithText("Long Tones").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("$FAST_BPM BPM").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun a_tone_held_into_the_microphone_is_measured_while_it_sounds() = runComposeUiTest {
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { LongTones(LongToneHost(), microphone) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the count-in") { shows("Get ready") }
        awaitInRealTime("the exercise") { shows("No signal - play louder") }

        // The gate, the level meter and the spectrum are all fed by the same frames.
        microphone.frequencyHz = A4_HZ
        awaitInRealTime("the tone to be heard") { shows("Signal detected") }

        // Held to the end of the note rather than stopped: what finishes it is the exercise's own
        // clock, running in the browser.
        awaitInRealTime("the end of the exercise") { shows("Exercise finished") }
    }

    @Test
    fun silence_is_not_reported_as_a_sound() = runComposeUiTest {
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { LongTones(LongToneHost(), microphone) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the exercise") { shows("No signal - play louder") }

        // Noise is a sound with no pitch in it. Loud enough to open the gate, so the exercise hears
        // something the whole way through, and it must still not read as a note.
        microphone.noisy = true
        awaitInRealTime("the noise to be heard") { shows("Signal detected") }
        awaitInRealTime("the end of the exercise") { shows("Exercise finished") }
        onNodeWithText("Average pitch: -").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runComposeUiTest {
        val host = LongToneHost()
        // At the trainer's own tempo the note lasts five seconds, which is what leaves room to
        // leave in the middle of one. Everywhere else these tests hurry the exercise along.
        setContent { LongTones(host, bpm = LongToneConfig().defaultTempo.bpm) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the running exercise") { shows("No signal - play louder") }

        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // Below STARTED the screen stops collecting, so a stopped exercise only shows on return -
        // the same on both platforms.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/** Fast enough that a count-in and a held note are a few seconds of a browser test. */
private const val FAST_BPM = 200

/** Concert A, comfortably inside the range the exercise looks for a pitch in. */
private const val A4_HZ = 440.0

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Everything [LongToneRoute] expects of a host: a ViewModel store and a lifecycle. */
private class LongToneHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun LongTones(
    host: LongToneHost,
    microphone: FakeMicrophone = FakeMicrophone(frameDurationMs = 10),
    bpm: Int = FAST_BPM,
) {
    val settings = FakeSettingsRepository(AppSettings(countInClick = false))
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            LongToneRoute(
                audioCapture = microphone,
                newMetronome = { SilentMetronome() },
                settingsRepository = settings,
                onBack = {},
                // The one place this trainer is configured from is its config, so the ViewModel is
                // built here rather than left to the route's default: at the default sixty beats a
                // minute a count-in and a five-beat note are nine seconds of browser test.
                viewModel = LongToneViewModel(
                    audioCapture = microphone,
                    settingsRepository = settings,
                    metronome = SilentMetronome(),
                    config = LongToneConfig(defaultTempo = Tempo(bpm)),
                ),
            )
        }
    }
}

/** The count-in is silent in these tests, so this is only what the ViewModel is handed. */
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

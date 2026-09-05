@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.fingering

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
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.awaitInRealTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * The fingering trainer, running in a browser.
 *
 * The exercise's own arithmetic is checked on both targets by `FingeringViewModelTest` against a
 * virtual clock. What is checked here is everything that test cannot see: the shared ViewModel and
 * the shared screen on wasmJs, driven by the browser's real clock, with the real pitch pipeline
 * listening to a real tone.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class FingeringOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runComposeUiTest {
        setContent { Fingering(FingeringHost()) }

        onNodeWithText("Fingering Trainer").assertIsDisplayed()
        onNodeWithText("Beginner").performScrollTo().assertIsDisplayed()
        onNodeWithText("Mixed order").performScrollTo().assertIsDisplayed()
        onNodeWithText("Play the notes").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun changing_the_setup_goes_through_the_view_model_and_comes_back() = runComposeUiTest {
        setContent { Fingering(FingeringHost()) }

        onNodeWithText("Beginner plus").performScrollTo().performClick()
        // The row that reads as selected is drawn from the ViewModel's state, so seeing the wider
        // note set is the round trip rather than the click.
        waitUntil("the wider note set", TIMEOUT) { shows("Beginner plus") }
        onNodeWithText("8 s").performScrollTo().performClick()

        onNodeWithText("Start").performClick()
        awaitInRealTime("the exercise") { shows("Stop") }
    }

    @Test
    fun a_preset_arrives_as_a_ready_made_exercise() = runComposeUiTest {
        setContent { Fingering(FingeringHost(), preset = twoNotes(autoStart = false)) }

        // No setup at all: the exercise was decided elsewhere and this only says what it will ask.
        onNodeWithText("Ready").assertIsDisplayed()
        assertFalse(shows("Mixed order"), "a preset offered the setup it was meant to replace")
        onNodeWithText("4 seconds per note, in order").assertIsDisplayed()
    }

    @Test
    fun the_note_played_into_the_microphone_is_the_one_the_trainer_hears() = runComposeUiTest {
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent {
            Fingering(FingeringHost(), preset = twoNotes(checkPlaying = true), microphone)
        }

        // The exercise starts by itself and asks for a note. Everything from here is the browser's
        // real clock and the real gate-YIN-lookup pipeline, in Wasm.
        awaitInRealTime("the first note") { shows("Play the note") }

        microphone.frequencyHz = SOUNDING_C4_HZ
        awaitInRealTime("the note to be recognised") { shows("That is the note") }
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runComposeUiTest {
        val host = FingeringHost()
        setContent { Fingering(host, preset = twoNotes()) }

        awaitInRealTime("the running exercise") { shows("Stop") }

        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // Below STARTED the screen stops collecting, so a stopped exercise only shows on return -
        // the same on both platforms.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/** Two notes, four seconds each, so a run is short enough to watch in real time. */
private fun twoNotes(checkPlaying: Boolean = false, autoStart: Boolean = true) = FingeringPreset(
    notes = listOf(Note(NoteName.C, 4), Note(NoteName.D, 4)),
    order = NoteOrder.ORDERED,
    secondsPerNote = 4,
    checkPlaying = checkPlaying,
    autoStart = autoStart,
)

/**
 * What a microphone hears when a written C4 is played on the default instrument. The trainer
 * transposes the written note itself; this is the sound that comes back.
 */
private val SOUNDING_C4_HZ = Instrument.TENOR_SAX.transposition
    .concertFor(Note(NoteName.C, 4))
    .frequencyHz()

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Everything [FingeringRoute] expects of a host: a ViewModel store and a lifecycle. */
private class FingeringHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun Fingering(
    host: FingeringHost,
    preset: FingeringPreset? = null,
    microphone: FakeMicrophone = FakeMicrophone(frameDurationMs = 10),
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            FingeringRoute(
                settingsRepository = FakeSettingsRepository(),
                audioCapture = microphone,
                newMetronome = { SilentMetronome() },
                onBack = {},
                preset = preset,
            )
        }
    }
}

/** A click with no sound: the count-in is counted on the clock either way. */
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

/** Generous: these waits are on the browser's clock, not a virtual one. */
private const val TIMEOUT = 10_000L

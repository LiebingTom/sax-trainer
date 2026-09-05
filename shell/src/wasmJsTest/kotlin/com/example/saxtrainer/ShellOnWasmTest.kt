@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import androidx.navigation.compose.composable
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.MetronomeEngineState
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.navigation.PlatformTrainers
import com.example.saxtrainer.navigation.SaxTrainerDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * The whole application, in a browser, started exactly as `:webApp` starts it.
 *
 * This is what M7 is for: one shell, one navigation graph and one set of screens, reached on the
 * web through the same code Android reaches them through. Each destination is walked to from the
 * landing page and left again, because a route that exists but cannot be got to is not navigation.
 */
class ShellOnWasmTest {

    @Test
    fun the_landing_page_offers_what_the_web_can_run_and_nothing_else() = runComposeUiTest {
        setContent { App(ShellHost()) }

        onNodeWithText("Sax Trainer").assertIsDisplayed()
        // The three learning units, each trimmed to the lessons a browser can teach.
        onNodeWithText("Your first five notes").assertIsDisplayed()
        onNodeWithText("The whole octave").performScrollTo().assertIsDisplayed()
        onNodeWithText("B flat and F sharp").performScrollTo().assertIsDisplayed()
        onNodeWithText("Note Quiz").performScrollTo().assertIsDisplayed()
        onNodeWithText("Metronome").performScrollTo().assertIsDisplayed()
        onNodeWithText("Settings").performScrollTo().assertIsDisplayed()

        // The microphone trainers are Android-only until M8, so they are not offered here.
        listOf("Fingering Trainer", "Note Trainer", "Long Tones", "Sequence Trainer").forEach {
            assertFalse(shows(it), "$it was offered on a platform that cannot run it")
        }
    }

    @Test
    fun every_destination_the_web_has_can_be_reached_and_left() = runComposeUiTest {
        setContent { App(ShellHost()) }

        // Each destination is recognised by something only it shows, not by its title: several
        // titles are also the text of the card that leads to them.
        openAndReturn(card = "Note Quiz", shownThere = "How many")
        openAndReturn(card = "Metronome", shownThere = "Tap tempo")
        openAndReturn(card = "Settings", shownThere = "Note naming")
        openAndReturn(card = "Your first five notes", shownThere = "Read them")
    }

    @Test
    fun a_lesson_starts_the_trainer_it_names() = runComposeUiTest {
        setContent { App(ShellHost()) }

        onNodeWithText("Your first five notes").performScrollTo().performClick()
        waitUntil("the lesson list", TIMEOUT) { shows("Read them") }
        // Trimmed to what a browser can teach: the fingering and playing lessons are not here.
        assertFalse(shows("Finger them"), "a lesson with no trainer to run it was offered")

        onNodeWithText("Read them").performClick()

        // The quiz, started by the lesson rather than from the Practice card, already running.
        waitUntil("the quiz", TIMEOUT) { shows("1 / 10") }
        onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun a_platform_can_add_a_trainer_of_its_own() = runComposeUiTest {
        // What `:app` does with the microphone trainers, in miniature: name the trainer, register
        // its destination, and it appears on the landing page and can be reached.
        val platform = PlatformTrainers(ids = setOf(TrainerId.LONG_TONES)) { _, _ ->
            composable(SaxTrainerDestination.LONG_TONES) { Text(ADDED_DESTINATION) }
        }
        setContent { App(ShellHost(), platform) }

        onNodeWithText("Long Tones").performScrollTo().performClick()

        waitUntil("the added destination", TIMEOUT) { shows(ADDED_DESTINATION) }
    }
}

private const val ADDED_DESTINATION = "a destination the platform added"

/** Taps a card on the landing page, checks where it went, and comes back. */
private fun ComposeUiTest.openAndReturn(card: String, shownThere: String) {
    onNodeWithText(card).performScrollTo().performClick()
    waitUntil("$card to open", TIMEOUT) { shows(shownThere) }

    onNodeWithContentDescription("Back").performClick()
    waitUntil("the landing page", TIMEOUT) { shows("Daily practice tools for saxophone") }
}

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** What an Activity or a `ComposeViewport` provides: a ViewModel store and a lifecycle. */
private class ShellHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun App(host: ShellHost, platform: PlatformTrainers = PlatformTrainers.None) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerApp(container = webShapedContainer(), platform = platform)
    }
}

/**
 * The container `:webApp` builds, with the two things a headless browser cannot give us swapped
 * for fakes. Nothing the shell registers uses either of them - the microphone trainers are their
 * only callers - but `AppContainer` still has to be handed something.
 */
private fun webShapedContainer() = AppContainer(
    settingsRepository = FakeSettingsRepository(),
    audioCapture = FakeMicrophone(frameDurationMs = 10),
    newMetronome = MetronomeFactory { SilentMetronome() },
)

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

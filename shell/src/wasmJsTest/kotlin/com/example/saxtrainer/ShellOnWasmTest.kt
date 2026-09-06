@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.SilentMetronome
import com.example.saxtrainer.core.testing.awaitInRealTime
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.scrolledTo
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test

/**
 * The whole application, in a browser, started exactly as `:webApp` starts it.
 *
 * One shell, one navigation graph and one set of screens, reached on the web through the same code
 * Android reaches them through. Each destination is walked to from the landing page and left again,
 * because a route that exists but cannot be got to is not navigation.
 *
 * Since M8 that means every destination: the microphone trainers are shared too, so the browser
 * offers the whole catalog and every practice tool rather than the subset it could run before.
 */
class ShellOnWasmTest {

    @Test
    fun the_landing_page_offers_the_whole_app() = runScreenTest { host ->
        setContent { App(host) }

        onNodeWithText("Sax Trainer").assertIsDisplayed()
        listOf(
            // The three learning units, each with every lesson it was written with.
            "Your first five notes",
            "The whole octave",
            "B flat and F sharp",
            // Every practice tool, microphone or not.
            "Note Quiz",
            "Fingering Trainer",
            "Note Trainer",
            "Long Tones",
            "Sequence Trainer",
            "Metronome",
            "Settings",
        ).forEach { scrolledTo(it).assertIsDisplayed() }
    }

    @Test
    fun every_destination_can_be_reached_and_left() = runScreenTest { host ->
        setContent { App(host) }

        // Each destination is recognised by something only it shows, not by its title: several
        // titles are also the text of the card that leads to them.
        openAndReturn(card = "Note Quiz", shownThere = "How many")
        openAndReturn(card = "Fingering Trainer", shownThere = "Play the notes")
        openAndReturn(card = "Note Trainer", shownThere = "Show the fingering")
        openAndReturn(card = "Long Tones", shownThere = "Starting note")
        openAndReturn(card = "Sequence Trainer", shownThere = "Add inverse")
        openAndReturn(card = "Metronome", shownThere = "Tap tempo")
        openAndReturn(card = "Settings", shownThere = "Note naming")
        openAndReturn(card = "Your first five notes", shownThere = "Read them")
    }

    @Test
    fun a_lesson_starts_the_trainer_it_names() = runScreenTest { host ->
        setContent { App(host) }

        scrolledTo("Your first five notes").performClick()
        waitUntil("the lesson list", BROWSER_TIMEOUT_MS) { shows("Read them") }

        onNodeWithText("Read them").performClick()

        // The quiz, started by the lesson rather than from the Practice card, already running.
        waitUntil("the quiz", BROWSER_TIMEOUT_MS) { shows("1 / 10") }
        onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun a_lesson_that_needs_a_microphone_starts_too() = runScreenTest { host ->
        setContent { App(host) }

        scrolledTo("Your first five notes").performClick()
        waitUntil("the lesson list", BROWSER_TIMEOUT_MS) { shows("Finger them") }

        onNodeWithText("Finger them").performClick()

        // A ready-made exercise that starts by itself, so reaching the running trainer is the whole
        // path: the lesson's preset, the fingering destination and the microphone behind it.
        awaitInRealTime("the fingering exercise") { shows("Stop") }
    }
}

/** Taps a card on the landing page, checks where it went, and comes back. */
private fun ComposeUiTest.openAndReturn(card: String, shownThere: String) {
    scrolledTo(card).performClick()
    waitUntil("$card to open", BROWSER_TIMEOUT_MS) { shows(shownThere) }

    onNodeWithContentDescription("Back").performClick()
    waitUntil("$card to close", BROWSER_TIMEOUT_MS) { !shows(shownThere) }
    // The landing page keeps where it was scrolled to, so the card that led here is back on screen
    // rather than the top of the list.
    scrolledTo(card).assertIsDisplayed()
}

@Composable
private fun App(host: ScreenHost) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerApp(container = webShapedContainer())
    }
}

/**
 * The container `:webApp` builds, with the things a headless browser cannot give us swapped for
 * fakes: a microphone that plays a tone on demand, a click with no sound, and stores that keep
 * nothing past the test.
 */
private fun webShapedContainer() = AppContainer(
    settingsRepository = FakeSettingsRepository(),
    exerciseSetups = InMemoryExerciseSetupRepository(),
    audioCapture = FakeMicrophone(frameDurationMs = 10),
    newMetronome = MetronomeFactory { SilentMetronome() },
)

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
 * Android reaches them through. Each destination is walked to and left again, because a route that
 * exists but cannot be got to is not navigation - the tools through Practice and the units through
 * Lessons, which is the way a player reaches them.
 *
 * Since M8 that means every destination: the microphone trainers are shared too, so the browser
 * offers the whole catalog and every practice tool rather than the subset it could run before.
 */
class ShellOnWasmTest {

    @Test
    fun the_landing_page_offers_the_whole_app() = runScreenTest { host ->
        setContent { App(host) }

        onNodeWithText("Sax Trainer").assertIsDisplayed()
        // Nothing is starred, so the landing page is the hint and the four ways on.
        listOf(
            "Star a tool in Practice to keep it here.",
            "Practice",
            "Lessons",
            "My Lessons",
            "Settings",
        ).forEach { scrolledTo(it).assertIsDisplayed() }

        scrolledTo("Practice").performClick()
        waitUntil("the catalogue", BROWSER_TIMEOUT_MS) { shows("Know your notes") }
        listOf(
            // Every practice tool, microphone or not.
            "Note Quiz",
            "Note Trainer",
            "Long Tones",
            "Sequence Trainer",
            "Metronome",
        ).forEach { scrolledTo(it).assertIsDisplayed() }
        onNodeWithContentDescription("Back").performClick()

        scrolledTo("Lessons").performClick()
        waitUntil("the curriculum", BROWSER_TIMEOUT_MS) { shows("C major, two octaves") }
        listOf(
            // Every learning unit, each with every lesson it was written with.
            "C major, two octaves",
            "Adding F sharp and C sharp",
            "Chromatic, with sharps",
            "Chromatic, sharps and flats",
        ).forEach { scrolledTo(it).assertIsDisplayed() }
    }

    @Test
    fun every_destination_can_be_reached_and_left() = runScreenTest { host ->
        setContent { App(host) }

        // Each destination is recognised by something only it shows, not by its title: several
        // titles are also the text of the card that leads to them.
        openAndReturn(card = "Settings", shownThere = "Note naming")
        openAndReturn(card = "Practice", shownThere = "Know your notes")
        openAndReturn(card = "Lessons", shownThere = "C major, two octaves")
        openAndReturn(
            card = "My Lessons",
            shownThere = "Tap to practise. Edit opens the setup it was saved with.",
        )

        // The tools live behind Practice now, so the walk to each one starts there.
        scrolledTo("Practice").performClick()
        waitUntil("the catalogue", BROWSER_TIMEOUT_MS) { shows("Know your notes") }
        openAndReturn(card = "Note Quiz", shownThere = "How many")
        openAndReturn(card = "Note Trainer", shownThere = "Show the fingering")
        openAndReturn(card = "Long Tones", shownThere = "Starting note")
        openAndReturn(card = "Sequence Trainer", shownThere = "Add inverse")

        // The metronome's own settings screen, then the click itself one Start tap beyond it.
        openAndReturn(card = "Metronome", shownThere = "Time signature")
        scrolledTo("Metronome").performClick()
        waitUntil("the metronome settings", BROWSER_TIMEOUT_MS) { shows("Time signature") }
        onNodeWithText("Start").performClick()
        waitUntil("the metronome", BROWSER_TIMEOUT_MS) { shows("Tap tempo") }
        onNodeWithContentDescription("Back").performClick()
        waitUntil("the metronome settings again", BROWSER_TIMEOUT_MS) { shows("Time signature") }
        onNodeWithContentDescription("Back").performClick()

        onNodeWithContentDescription("Back").performClick()

        scrolledTo("Lessons").performClick()
        waitUntil("the curriculum", BROWSER_TIMEOUT_MS) { shows("C major, two octaves") }
        openAndReturn(card = "C major, two octaves", shownThere = "Name and grip them")
    }

    @Test
    fun a_starred_tool_reaches_the_landing_page_and_leaves_it() = runScreenTest { host ->
        setContent { App(host) }

        scrolledTo("Practice").performClick()
        waitUntil("the catalogue", BROWSER_TIMEOUT_MS) { shows("Know your notes") }

        onNodeWithContentDescription("Favorite Metronome").performClick()
        onNodeWithContentDescription("Back").performClick()

        // A card of its own on the landing page, where the hint used to be.
        waitUntil("the starred tool", BROWSER_TIMEOUT_MS) {
            !shows("Star a tool in Practice to keep it here.")
        }
        scrolledTo("Metronome").assertIsDisplayed()

        onNodeWithContentDescription("Remove Metronome from favorites").performClick()

        waitUntil("the hint", BROWSER_TIMEOUT_MS) {
            shows("Star a tool in Practice to keep it here.")
        }
    }

    @Test
    fun a_lesson_starts_the_trainer_it_names() = runScreenTest { host ->
        setContent { App(host) }

        scrolledTo("Lessons").performClick()
        waitUntil("the curriculum", BROWSER_TIMEOUT_MS) { shows("C major, two octaves") }
        scrolledTo("C major, two octaves").performClick()
        waitUntil("the lesson list", BROWSER_TIMEOUT_MS) { shows("Name and grip them") }

        onNodeWithText("Name and grip them").performClick()

        // The quiz, started by the lesson rather than from the Practice card, already running.
        waitUntil("the quiz", BROWSER_TIMEOUT_MS) { shows("1 / 20") }
        onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun a_lesson_that_needs_a_microphone_starts_too() = runScreenTest { host ->
        setContent { App(host) }

        scrolledTo("Lessons").performClick()
        waitUntil("the curriculum", BROWSER_TIMEOUT_MS) { shows("C major, two octaves") }
        scrolledTo("C major, two octaves").performClick()
        waitUntil("the lesson list", BROWSER_TIMEOUT_MS) { shows("Play them") }

        onNodeWithText("Play them").performClick()

        // A ready-made exercise that starts by itself, so reaching the running trainer is the whole
        // path: the lesson's preset, the note trainer destination and the microphone behind it.
        awaitInRealTime("the note trainer exercise") { shows("Stop") }
    }
}

/** Taps a card on the list in front of us, checks where it went, and comes back. */
private fun ComposeUiTest.openAndReturn(card: String, shownThere: String) {
    scrolledTo(card).performClick()
    waitUntil("$card to open", BROWSER_TIMEOUT_MS) { shows(shownThere) }

    onNodeWithContentDescription("Back").performClick()
    waitUntil("$card to close", BROWSER_TIMEOUT_MS) { !shows(shownThere) }
    // The list keeps where it was scrolled to, so the card that led here is back on screen rather
    // than the top of it.
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

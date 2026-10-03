@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The settings screen in a browser, over the shared ViewModel and the shared repository interface.
 *
 * Where the settings are actually kept is `:core:settings`' business - `localStorage` here,
 * SharedPreferences on Android - and is tested there. What this checks is that a tap reaches the
 * repository and the repository's answer comes back to the screen.
 */
class SettingsOnWasmTest {

    @Test
    fun every_section_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Settings(host) }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Instrument").assertIsDisplayed()
        onNodeWithText("Note naming").performScrollTo().assertIsDisplayed()
        onNodeWithText("Octave numbers").performScrollTo().assertIsDisplayed()
        onNodeWithText("Count-in").performScrollTo().assertIsDisplayed()
        onNodeWithText("Note display").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosing_a_naming_is_written_through_and_read_back() = runScreenTest { host ->
        val settings = FakeSettingsRepository()
        setContent { Settings(host, settings) }

        onNodeWithText(NoteNaming.GERMAN.displayName).performScrollTo().performClick()

        waitUntil("the stored naming", BROWSER_TIMEOUT_MS) {
            settings.settings.value.noteNaming == NoteNaming.GERMAN
        }
        onNodeWithText(NoteNaming.GERMAN.displayName).assertIsSelected()
        onNodeWithText(NoteNaming.INTERNATIONAL.displayName).assertIsNotSelected()

        // And the other way round: a change made anywhere else shows up on the screen.
        settings.setInstrument(Instrument.ALTO_SAX)
        waitForIdle()
        onNodeWithText(Instrument.ALTO_SAX.displayName).performScrollTo().assertIsSelected()
        assertEquals(
            NoteNaming.GERMAN,
            settings.settings.value.noteNaming,
            "the naming survived the second change",
        )
    }

    @Test
    fun choosing_an_octave_numbering_is_written_through() = runScreenTest { host ->
        val settings = FakeSettingsRepository()
        setContent { Settings(host, settings) }

        onNodeWithText(OctaveNumbering.SAXOPHONE.displayName).performScrollTo().performClick()

        waitUntil("the stored octave numbering", BROWSER_TIMEOUT_MS) {
            settings.settings.value.octaveNumbering == OctaveNumbering.SAXOPHONE
        }
        onNodeWithText(OctaveNumbering.SAXOPHONE.displayName).assertIsSelected()
        onNodeWithText(OctaveNumbering.SCIENTIFIC.displayName).assertIsNotSelected()
    }

    @Test
    fun a_count_in_length_is_chosen_through_the_shared_view_model() = runScreenTest { host ->
        val settings = FakeSettingsRepository()
        setContent { Settings(host, settings) }

        onNodeWithText("4 beats").performScrollTo().performClick()

        waitUntil("the stored count-in", BROWSER_TIMEOUT_MS) { settings.settings.value.countInBeats == 4 }
    }
}

@Composable
private fun Settings(
    host: ScreenHost,
    settings: SettingsRepository = FakeSettingsRepository(),
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            SettingsRoute(settingsRepository = settings, onBack = {})
        }
    }
}

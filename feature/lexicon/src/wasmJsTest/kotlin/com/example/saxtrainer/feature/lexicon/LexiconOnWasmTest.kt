@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.lexicon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test

/** The lexicon in a browser: the shared ViewModel and screen, with nothing Android underneath. */
class LexiconOnWasmTest {

    @Test
    fun the_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Lexicon(host) }

        onNodeWithText("Fingering Lexicon").assertIsDisplayed()
        onNodeWithText("C4").assertIsDisplayed()
        onNodeWithText("Standard").assertIsDisplayed()
        onNodeWithText("Higher").assertIsDisplayed()
    }

    @Test
    fun choosing_b_flat_shows_its_alternates() = runScreenTest { host ->
        setContent { Lexicon(host) }

        // One A sharp chip per octave: low B flat's, then the one in octave 4.
        onAllNodesWithText("A♯/B♭")[1].performScrollTo().performClick()

        waitUntil("B flat's grips", BROWSER_TIMEOUT_MS) { shows("Side B♭") }
        onNodeWithText("A♯4").assertIsDisplayed()
        onNodeWithText("B♭4").assertIsDisplayed()
    }

    @Test
    fun the_arrows_step_through_the_notes() = runScreenTest { host ->
        setContent { Lexicon(host) }

        onNodeWithText("Higher").performClick()

        waitUntil("the next note", BROWSER_TIMEOUT_MS) { shows("D♭4") }
    }
}

@Composable
private fun Lexicon(host: ScreenHost) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            LexiconRoute(settingsRepository = FakeSettingsRepository(), onBack = {})
        }
    }
}

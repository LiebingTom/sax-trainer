@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.web

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.SaxTrainerApp
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.scrolledTo
import kotlin.test.Test

/**
 * The page's own wiring, in a browser.
 *
 * `:shell` is exercised over fakes elsewhere; what is checked here is the half only this module
 * owns - that the container built from the browser's own `localStorage`, `AudioContext` and
 * `getUserMedia` implementations starts the app and that its screens work. `ComposeViewport` is
 * the one line `main` has that a test cannot stand in for, and it is the same call for every
 * Compose web application.
 */
class WebEntryPointTest {

    @Test
    fun the_container_the_page_builds_starts_the_app() = runScreenTest { host ->
        setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides host,
                LocalLifecycleOwner provides host,
            ) {
                SaxTrainerApp(container = webContainer())
            }
        }

        onNodeWithText("Sax Trainer").assertIsDisplayed()
        scrolledTo("Practice").performClick()
        waitUntil("the catalogue", BROWSER_TIMEOUT_MS) {
            onAllNodesWithText("Note Quiz").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settings_kept_in_the_browser_reach_the_screen() = runScreenTest { host ->
        setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides host,
                LocalLifecycleOwner provides host,
            ) {
                SaxTrainerApp(container = webContainer())
            }
        }

        scrolledTo("Settings").performClick()

        // Drawn from LocalStorageSettingsRepository, which is what the page really uses.
        waitUntil("the settings screen", BROWSER_TIMEOUT_MS) {
            onAllNodesWithText("Note naming").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

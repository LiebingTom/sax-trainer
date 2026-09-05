@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.web

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.SaxTrainerApp
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
    fun the_container_the_page_builds_starts_the_app() = runComposeUiTest {
        val host = WebHost()
        setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides host,
                LocalLifecycleOwner provides host,
            ) {
                SaxTrainerApp(container = webContainer())
            }
        }

        onNodeWithText("Sax Trainer").assertIsDisplayed()
        scrolledTo("Note Quiz").assertIsDisplayed()
    }

    @Test
    fun settings_kept_in_the_browser_reach_the_screen() = runComposeUiTest {
        val host = WebHost()
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
        waitUntil("the settings screen", TIMEOUT) {
            onAllNodesWithText("Note naming").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

/**
 * The landing page is a lazy list, so a card further down it has not been composed yet and cannot
 * simply be scrolled to. This scrolls the list until it exists.
 */
private fun ComposeUiTest.scrolledTo(text: String): SemanticsNodeInteraction {
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
    return onNodeWithText(text)
}

/** What `ComposeViewport` provides in the page: a ViewModel store and a lifecycle. */
private class WebHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    private val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

/** Generous: these waits are on the browser's clock, not a virtual one. */
private const val TIMEOUT = 10_000L

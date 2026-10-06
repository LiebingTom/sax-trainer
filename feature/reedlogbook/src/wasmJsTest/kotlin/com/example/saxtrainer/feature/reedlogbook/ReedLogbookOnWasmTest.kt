@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.reed.InMemoryReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedSessions
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The logbook in a browser: the shared ViewModel and screen, with nothing Android underneath. */
class ReedLogbookOnWasmTest {

    private val oneReed = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", Instant.parse("2026-10-05T14:00:00Z"))

    @Test
    fun an_empty_logbook_offers_to_add_a_reed() = runScreenTest { host ->
        setContent { Logbook(host, InMemoryReedLogbookRepository()) }

        onNodeWithText("Reed Logbook").assertIsDisplayed()
        onNodeWithText("Add a reed").assertIsDisplayed()
        onNodeWithText("Start session").assertIsNotEnabled()
    }

    @Test
    fun a_session_starts_and_stops_on_the_selected_reed() = runScreenTest { host ->
        val repository = InMemoryReedLogbookRepository(oneReed)
        setContent { Logbook(host, repository) }

        onNodeWithText("Reed No. 1").assertIsDisplayed()
        onNodeWithText("Start session").assertIsEnabled().performClick()

        waitUntil("the session to run", BROWSER_TIMEOUT_MS) { shows("Playing No. 1") }
        assertNotNull(repository.logbook.value.runningSession)

        onNodeWithText("Stop session").performClick()
        waitUntil("the session to stop", BROWSER_TIMEOUT_MS) { repository.logbook.value.runningSession == null }
        assertNull(repository.logbook.value.runningSession)
    }
}

@Composable
private fun Logbook(host: ScreenHost, repository: InMemoryReedLogbookRepository) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            ReedLogbookRoute(
                repository = repository,
                sessions = ReedSessions(repository),
                onAdd = {},
                onHistory = {},
                onOpenReed = {},
                onBack = {},
            )
        }
    }
}

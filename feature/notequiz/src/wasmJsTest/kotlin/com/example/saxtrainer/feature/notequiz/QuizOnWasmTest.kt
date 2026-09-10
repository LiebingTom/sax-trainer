@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.notequiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.labels
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The quiz, running in a browser: the shared ViewModel, the shared state and the shared screen,
 * with nothing Android underneath them.
 *
 * The ViewModel comes from `viewModel { }` and the state is read through
 * `collectAsStateWithLifecycle`, exactly as on Android, because that is the thing worth proving -
 * that the multiplatform lifecycle artifacts carry the app's existing pattern onto wasmJs rather
 * than merely compiling against it.
 *
 * One habit of this harness shapes the tests below: `waitUntil` holds the browser's single thread,
 * so a `delay` scheduled on [kotlinx.coroutines.Dispatchers.Default] - a `setTimeout` here - never
 * gets to run while the test is waiting. Nothing is asserted about the quiz's reveal pause for that
 * reason; the run is driven by the second tap that skips it, which is real behaviour and needs no
 * clock.
 */
class QuizOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Quiz(host) }

        onNodeWithText("Note Quiz").assertIsDisplayed()
        onNodeWithText("Notes").assertIsDisplayed()
        onNodeWithText("How many").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun a_preset_quiz_is_played_through_to_its_result() = runScreenTest { host ->
        var finished: QuizResult? = null
        setContent {
            Quiz(
                host = host,
                preset = QuizPreset(notes = QUIZ_NOTES, questionCount = 2),
                onExerciseFinished = { finished = it },
            )
        }

        // Auto-started by the preset, so a question is on screen without anyone pressing Start.
        waitUntil("the first question", BROWSER_TIMEOUT_MS) { shows("1 / 2") }
        onNodeWithText("Stop").assertIsDisplayed()

        answerAndMoveOn()
        waitUntil("the second question", BROWSER_TIMEOUT_MS) { shows("2 / 2") }
        answerAndMoveOn()

        waitUntil("the result", BROWSER_TIMEOUT_MS) { shows("Quiz complete") }
        assertEquals(2, finished?.answers?.size, "both answers reached the caller")
        onNodeWithText("Again").assertIsDisplayed()
    }

    @Test
    fun a_settings_change_reaches_the_screen_through_the_collected_state() = runScreenTest { host ->
        val settings = FakeSettingsRepository()
        setContent {
            Quiz(
                host = host,
                settings = settings,
                preset = QuizPreset(
                    notes = QUIZ_NOTES,
                    mode = QuizMode.FINGERING_TO_NOTE,
                    questionCount = 2,
                ),
            )
        }

        // In this mode every option is labelled with a note name, so how notes are named is
        // visible on screen - and B flat is the note the two namings disagree about.
        waitUntil("the international names", BROWSER_TIMEOUT_MS) { labels("B♭4") }

        settings.setNoteNaming(NoteNaming.GERMAN)

        waitUntil("the German names", BROWSER_TIMEOUT_MS) { labels("B4") }
    }

    @Test
    fun going_to_the_background_stops_the_quiz() = runScreenTest { host ->
        setContent { Quiz(host, preset = QuizPreset(notes = QUIZ_NOTES, questionCount = 10)) }

        waitUntil("the first question", BROWSER_TIMEOUT_MS) { shows("1 / 10") }

        // What LifecycleEventEffect(ON_STOP) watches for: a quiz the player cannot see must not
        // keep counting down to its next question.
        host.leaveAndReturn()

        waitUntil("the stopped result", BROWSER_TIMEOUT_MS) { shows("Stopped early") }
    }

    /**
     * Answers the question, then taps again.
     *
     * The second tap is what the player does when they have read the feedback and do not want to
     * wait out the rest of the pause; here it is also what moves the quiz on without a real clock.
     */
    private fun ComposeUiTest.answerAndMoveOn() {
        onNodeWithContentDescription("Fingering 1").performClick()
        waitForIdle()
        onNodeWithContentDescription("Fingering 1").performClick()
    }
}

@Composable
private fun Quiz(
    host: ScreenHost,
    settings: SettingsRepository = FakeSettingsRepository(),
    preset: QuizPreset? = null,
    onExerciseFinished: (QuizResult) -> Unit = {},
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            QuizRoute(
                settingsRepository = settings,
                exerciseSetups = InMemoryExerciseSetupRepository(),
                onBack = {},
                preset = preset,
                onExerciseFinished = onExerciseFinished,
            )
        }
    }
}

/** Four notes the tenor chart knows, one of them the B flat the two namings disagree about. */
private val QUIZ_NOTES = listOf(
    Note(NoteName.G, 4),
    Note(NoteName.A, 4),
    Note(NoteName.A_SHARP, 4),
    Note(NoteName.C, 5),
)

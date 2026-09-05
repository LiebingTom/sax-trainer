@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.notequiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.FakeSettingsRepository
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
    fun the_setup_screen_is_drawn_from_shared_code() = runComposeUiTest {
        setContent { Quiz(QuizHost()) }

        onNodeWithText("Note Quiz").assertIsDisplayed()
        onNodeWithText("Notes").assertIsDisplayed()
        onNodeWithText("How many").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun a_preset_quiz_is_played_through_to_its_result() = runComposeUiTest {
        var finished: QuizResult? = null
        setContent {
            Quiz(
                host = QuizHost(),
                preset = QuizPreset(notes = QUIZ_NOTES, questionCount = 2),
                onExerciseFinished = { finished = it },
            )
        }

        // Auto-started by the preset, so a question is on screen without anyone pressing Start.
        waitUntil("the first question", TIMEOUT) { shows("1 / 2") }
        onNodeWithText("Stop").assertIsDisplayed()

        answerAndMoveOn()
        waitUntil("the second question", TIMEOUT) { shows("2 / 2") }
        answerAndMoveOn()

        waitUntil("the result", TIMEOUT) { shows("Quiz complete") }
        assertEquals(2, finished?.answers?.size, "both answers reached the caller")
        onNodeWithText("Again").assertIsDisplayed()
    }

    @Test
    fun a_settings_change_reaches_the_screen_through_the_collected_state() = runComposeUiTest {
        val settings = FakeSettingsRepository()
        setContent {
            Quiz(
                host = QuizHost(),
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
        waitUntil("the international names", TIMEOUT) { labels("B♭4") }

        settings.setNoteNaming(NoteNaming.GERMAN)

        waitUntil("the German names", TIMEOUT) { labels("B4") }
    }

    @Test
    fun going_to_the_background_stops_the_quiz() = runComposeUiTest {
        val host = QuizHost()
        setContent { Quiz(host, preset = QuizPreset(notes = QUIZ_NOTES, questionCount = 10)) }

        waitUntil("the first question", TIMEOUT) { shows("1 / 10") }

        // What LifecycleEventEffect(ON_STOP) watches for: a quiz the player cannot see must not
        // keep counting down to its next question.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // Coming back is part of the check rather than tidying up. Below STARTED the screen is not
        // collecting state, so the result only becomes visible on return - the same on both
        // platforms, and the reason this cannot be asserted while backgrounded.
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        waitUntil("the stopped result", TIMEOUT) { shows("Stopped early") }
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

private fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

private fun ComposeUiTest.labels(contentDescription: String): Boolean =
    onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isNotEmpty()

/** Everything [QuizRoute] expects of a host: somewhere to keep a ViewModel, and a lifecycle. */
private class QuizHost : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore = ViewModelStore()
    val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

@Composable
private fun Quiz(
    host: QuizHost,
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

/** Generous: these waits are on the browser's clock, not a virtual one. */
private const val TIMEOUT = 10_000L

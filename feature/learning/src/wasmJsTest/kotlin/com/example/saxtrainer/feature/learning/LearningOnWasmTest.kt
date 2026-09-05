@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.learning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.InMemoryLessonProgressRepository
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.core.testing.BROWSER_TIMEOUT_MS
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lesson list in a browser.
 *
 * This feature has no ViewModel - it renders a unit it is handed and reports the lesson that was
 * tapped - so what is worth checking on wasmJs is the other half of the pattern:
 * `collectAsStateWithLifecycle` over a progress repository, and a card that reacts to a tap.
 */
class LearningOnWasmTest {

    @Test
    fun the_unit_and_its_lessons_are_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Learning(host, InMemoryLessonProgressRepository()) }

        onNodeWithText(UNIT.title).assertIsDisplayed()
        onNodeWithText(UNIT.description).assertIsDisplayed()
        onNodeWithText("0 of 2 finished").assertIsDisplayed()
        onNodeWithText("First five notes").assertIsDisplayed()
        // The second lesson needs the first, so it is on screen but not open yet.
        onNodeWithText("Locked").assertIsDisplayed()
        onNodeWithText("Play them").assertIsNotEnabled()
    }

    @Test
    fun tapping_an_open_lesson_reports_it() = runScreenTest { host ->
        var started: LessonId? = null
        setContent {
            Learning(host, InMemoryLessonProgressRepository(), onStartLesson = {
                started = it
            })
        }

        onNodeWithText("First five notes").performClick()

        waitUntil("the lesson to be reported", BROWSER_TIMEOUT_MS) { started != null }
        assertEquals(READING.value, started?.value, "a different lesson was reported")
    }

    @Test
    fun progress_recorded_elsewhere_reaches_the_list() = runScreenTest { host ->
        val progress = InMemoryLessonProgressRepository()
        setContent { Learning(host, progress) }

        progress.record(READING, ExerciseOutcome(completed = true, score = 0.8f))

        waitUntil("the finished lesson", BROWSER_TIMEOUT_MS) { shows("1 of 2 finished") }
        onNodeWithText("Done").assertIsDisplayed()
        // The next lesson opens as soon as the one it needs is finished.
        onNodeWithText("Play them").assertIsDisplayed()
    }
}

@Composable
private fun Learning(
    host: ScreenHost,
    progress: InMemoryLessonProgressRepository,
    onStartLesson: (LessonId) -> Unit = {},
) {
    CompositionLocalProvider(LocalLifecycleOwner provides host) {
        SaxTrainerTheme(darkTheme = false) {
            LearningRoute(
                unit = UNIT,
                progressRepository = progress,
                isUnlocked = { lesson ->
                    lesson.requires?.let(progress.progress.value::isCompleted) ?: true
                },
                onStartLesson = onStartLesson,
                onBack = {},
            )
        }
    }
}

/** A stand-in configuration: this module never looks inside one. */
private object NoLaunch : TrainerLaunch

private val READING = LessonId("reading")
private val PLAYING = LessonId("playing")

private val UNIT = LearningUnit(
    id = "unit-1",
    title = "Your first five notes",
    description = "Read them, then find them, then play them.",
    lessons = listOf(
        Lesson(
            id = READING,
            title = "First five notes",
            subtitle = "Name the note you see",
            steps = listOf(LessonStep(TrainerId.NOTE_QUIZ, NoLaunch)),
        ),
        Lesson(
            id = PLAYING,
            title = "Play them",
            subtitle = "Sound each one in turn",
            steps = listOf(LessonStep(TrainerId.NOTE_TRAINER, NoLaunch)),
            requires = READING,
        ),
    ),
)

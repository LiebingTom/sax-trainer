package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.feature.learning.LearningRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.notequiz.QuizRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.HomeScreen
import com.example.saxtrainer.home.learningEntriesOf
import com.example.saxtrainer.home.practiceGroupsOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf

/**
 * Central navigation graph, and the only place that knows both the destinations and the
 * dependencies a feature needs. Feature screens receive callbacks and dependencies, never the
 * [NavHostController], so features stay independent of each other and of the app module.
 *
 * It is also where a lesson meets a trainer: a lesson travels as nothing but its id and the
 * position of the step being run, and this graph turns those into the trainer's own configuration
 * and turns the trainer's own result back into lesson progress. Neither side knows the other
 * exists, which is what lets one lesson run several trainers in turn.
 *
 * What is registered here is what every platform has. [platform] adds the rest - today the
 * microphone trainers, which only Android can build - and the landing page and the catalog are
 * trimmed to match, so nothing on screen leads anywhere that does not exist.
 */
@Composable
fun SaxTrainerNavHost(
    container: AppContainer,
    platform: PlatformTrainers = PlatformTrainers.None,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val trainers = remember(platform) { SHELL_TRAINERS + platform.ids }
    val curriculum = remember(container, trainers) { container.curriculum.runnableWith(trainers) }

    NavHost(
        navController = navController,
        startDestination = SaxTrainerDestination.HOME,
        modifier = modifier,
    ) {
        composable(SaxTrainerDestination.HOME) {
            HomeScreen(
                onEntryClick = { route -> navController.navigate(route) },
                learning = learningEntriesOf(curriculum),
                practice = practiceGroupsOf(trainers),
            )
        }
        composable(SaxTrainerDestination.METRONOME) {
            MetronomeRoute(
                newMetronome = container.newMetronome,
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.NOTE_QUIZ_PATTERN,
            container = container,
            navController = navController,
        ) { lesson, stepIndex, finish ->
            QuizRoute(
                settingsRepository = container.settingsRepository,
                preset = lesson?.stepAt(stepIndex)?.let(::noteQuizPresetOf),
                onExerciseFinished = { result -> finish(noteQuizOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.LEARNING_UNIT,
            arguments = listOf(navArgument(UNIT_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val unit = entry.arguments
                ?.read { getStringOrNull(UNIT_ID_ARG) }
                ?.let(curriculum::unitOf)
            if (unit == null) {
                navController.popBackStack()
            } else {
                LearningRoute(
                    unit = unit,
                    progressRepository = container.lessonProgress,
                    isUnlocked = { lesson ->
                        curriculum.isUnlocked(lesson.id, container.lessonProgress.progress.value)
                    },
                    onStartLesson = { id ->
                        curriculum.lessonOf(id)?.let { lesson ->
                            // Starting a lesson starts it from the top: what a half-finished
                            // attempt had scored is not worth carrying into a new one.
                            container.lessonRuns.clear(id)
                            routeForStep(lesson, stepIndex = 0)?.let(navController::navigate)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(SaxTrainerDestination.SETTINGS) {
            SettingsRoute(
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() },
            )
        }

        platform.destinations(this, navController, container)
    }
}

/** The trainers this module carries itself, wherever it runs. */
private val SHELL_TRAINERS = setOf(TrainerId.NOTE_QUIZ, TrainerId.METRONOME)

/**
 * One trainer destination that may be running a lesson step.
 *
 * Every preset-carrying trainer is registered the same way, so the rule for ending a step - keep
 * what it scored, then either run the next one or write the lesson's combined outcome - lives in
 * exactly one place instead of being copied into each of them. Public because the trainers a
 * platform adds for itself have to be registered on the same terms as the ones here.
 */
fun NavGraphBuilder.lessonStep(
    pattern: String,
    container: AppContainer,
    navController: NavHostController,
    content: @Composable (
        lesson: Lesson?,
        stepIndex: Int,
        finish: (ExerciseOutcome) -> Unit,
    ) -> Unit,
) {
    composable(
        route = pattern,
        arguments = listOf(
            navArgument(LESSON_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(STEP_ARG) {
                type = NavType.IntType
                defaultValue = 0
            },
        ),
    ) { entry ->
        val arguments = entry.arguments
        val lessonId = arguments?.read { getStringOrNull(LESSON_ARG) }?.let(::LessonId)
        val lesson = lessonId?.let(container.curriculum::lessonOf)
        val stepIndex = arguments?.read { getIntOrNull(STEP_ARG) } ?: 0
        val currentRoute = entry.destination.route ?: pattern

        content(lesson, stepIndex) { outcome ->
            if (lessonId != null && lesson != null) {
                navController.finishStep(
                    container = container,
                    lesson = lesson,
                    lessonId = lessonId,
                    stepIndex = stepIndex,
                    outcome = outcome,
                    currentRoute = currentRoute,
                )
            }
        }
    }
}

/**
 * Ends one step of a lesson.
 *
 * Keeps what the step scored, then either runs the next one or writes the lesson's combined
 * outcome and leaves. A lesson of one step takes the second branch immediately, so a single-step
 * lesson behaves exactly as it always did and a multi-step one is the same code with more steps.
 * The trainer that just finished knows none of this - it reported a result and nothing more.
 */
private fun NavHostController.finishStep(
    container: AppContainer,
    lesson: Lesson,
    lessonId: LessonId,
    stepIndex: Int,
    outcome: ExerciseOutcome,
    currentRoute: String,
) {
    container.lessonRuns.record(lessonId, stepIndex, outcome)

    val next = routeForStep(lesson, stepIndex + 1)
    if (next == null) {
        container.lessonProgress.record(lessonId, container.lessonRuns.outcomeOf(lessonId))
        container.lessonRuns.clear(lessonId)
    } else {
        // Replacing the finished step rather than stacking on it, so Back leaves the lesson
        // instead of walking it in reverse.
        navigate(next) { popUpTo(currentRoute) { inclusive = true } }
    }
}

/**
 * Where one step of a lesson runs, or null past the last step - which is how a lesson ends.
 *
 * The single point at which a trainer is registered with the Learning layer: the branches are
 * exhaustive over [TrainerId], so a new trainer cannot be added without being given a home here.
 * A route for a trainer the platform does not have is unreachable rather than wrong: the catalog
 * is trimmed to what can be run before any lesson is offered.
 */
private fun routeForStep(lesson: Lesson, stepIndex: Int): String? =
    when (lesson.stepAt(stepIndex)?.trainerId) {
        TrainerId.NOTE_QUIZ -> SaxTrainerDestination.noteQuiz(lesson.id, stepIndex)
        TrainerId.FINGERING -> SaxTrainerDestination.fingering(lesson.id, stepIndex)
        TrainerId.NOTE_TRAINER -> SaxTrainerDestination.noteTrainer(lesson.id, stepIndex)
        TrainerId.SEQUENCE -> SaxTrainerDestination.sequence(lesson.id, stepIndex)
        TrainerId.LONG_TONES -> SaxTrainerDestination.LONG_TONES
        TrainerId.METRONOME -> SaxTrainerDestination.METRONOME
        null -> null
    }

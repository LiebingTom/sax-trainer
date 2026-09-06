package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
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
import com.example.saxtrainer.feature.fingering.FingeringRoute
import com.example.saxtrainer.feature.learning.LearningRoute
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.notequiz.QuizRoute
import com.example.saxtrainer.feature.notetrainer.NoteTrainerRoute
import com.example.saxtrainer.feature.sequence.SequenceRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.HomeScreen
import com.example.saxtrainer.home.learningEntriesOf
import com.example.saxtrainer.learning.catalog.steps.fingeringOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.fingeringPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.sequenceOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.sequencePresetOf

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
 * Every destination the app has is here, and every platform gets all of them. The microphone
 * trainers used to be registered by `:app` alone, because only Android could build them; since M8
 * they are shared like the rest, and what a browser does about a microphone is
 * [com.example.saxtrainer.core.audio.capture.AudioCapture]'s business rather than navigation's.
 */
@Composable
fun SaxTrainerNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SaxTrainerDestination.HOME,
        modifier = modifier,
    ) {
        composable(SaxTrainerDestination.HOME) {
            HomeScreen(
                onEntryClick = { route -> navController.navigate(route) },
                learning = learningEntriesOf(container.curriculum),
            )
        }
        composable(SaxTrainerDestination.METRONOME) {
            MetronomeRoute(
                newMetronome = container.newMetronome,
                exerciseSetups = container.exerciseSetups,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SaxTrainerDestination.LONG_TONES) {
            LongToneRoute(
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                settingsRepository = container.settingsRepository,
                exerciseSetups = container.exerciseSetups,
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
                exerciseSetups = container.exerciseSetups,
                preset = lesson?.stepAt(stepIndex)?.let(::noteQuizPresetOf),
                onExerciseFinished = { result -> finish(noteQuizOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.FINGERING_PATTERN,
            container = container,
            navController = navController,
        ) { lesson, stepIndex, finish ->
            // No lesson argument is the freely configurable Practice entry, which is what every
            // default in FingeringRoute describes.
            FingeringRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = container.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = lesson?.stepAt(stepIndex)?.let(::fingeringPresetOf),
                onExerciseFinished = { result -> finish(fingeringOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.NOTE_TRAINER_PATTERN,
            container = container,
            navController = navController,
        ) { lesson, stepIndex, finish ->
            NoteTrainerRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = container.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = lesson?.stepAt(stepIndex)?.let(::noteTrainerPresetOf),
                onExerciseFinished = { result -> finish(noteTrainerOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.SEQUENCE_PATTERN,
            container = container,
            navController = navController,
        ) { lesson, stepIndex, finish ->
            SequenceRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = container.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = lesson?.stepAt(stepIndex)?.let(::sequencePresetOf),
                onExerciseFinished = { result -> finish(sequenceOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.LEARNING_UNIT,
            arguments = listOf(navArgument(UNIT_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val unit = entry.arguments
                ?.read { getStringOrNull(UNIT_ID_ARG) }
                ?.let(container.curriculum::unitOf)
            if (unit == null) {
                navController.popBackStack()
            } else {
                LearningRoute(
                    unit = unit,
                    progressRepository = container.lessonProgress,
                    isUnlocked = { lesson ->
                        container.curriculum.isUnlocked(
                            lesson.id,
                            container.lessonProgress.progress.value,
                        )
                    },
                    onStartLesson = { id ->
                        container.curriculum.lessonOf(id)?.let { lesson ->
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
    }
}

/**
 * One trainer destination that may be running a lesson step.
 *
 * Every preset-carrying trainer is registered the same way, so the rule for ending a step - keep
 * what it scored, then either run the next one or write the lesson's combined outcome - lives in
 * exactly one place instead of being copied into each of them.
 */
private fun NavGraphBuilder.lessonStep(
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

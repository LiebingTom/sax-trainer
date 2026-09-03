package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.feature.fingering.FingeringRoute
import com.example.saxtrainer.feature.learning.LearningRoute
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.HomeScreen
import com.example.saxtrainer.learning.catalog.fingeringOutcomeOf
import com.example.saxtrainer.learning.catalog.fingeringPresetOf

/**
 * Central navigation graph, and the only place that knows both the destinations and the
 * dependencies a feature needs. Feature screens receive callbacks and dependencies, never the
 * [NavHostController], so features stay independent of each other and of the app module.
 *
 * It is also where a lesson meets a trainer: a lesson travels as nothing but its id, and this graph
 * turns that id into the trainer's own configuration and turns the trainer's own result back into
 * lesson progress. Neither side knows the other exists.
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
            HomeScreen(onEntryClick = { route -> navController.navigate(route) })
        }
        composable(SaxTrainerDestination.METRONOME) {
            MetronomeRoute(onBack = { navController.popBackStack() })
        }
        composable(SaxTrainerDestination.LONG_TONES) {
            LongToneRoute(
                audioCapture = container.audioCapture,
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.FINGERING_PATTERN,
            arguments = listOf(
                navArgument(LESSON_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            // No lesson argument is the freely configurable Practice entry, which is what every
            // default in FingeringRoute describes.
            val lessonId = entry.arguments?.getString(LESSON_ARG)?.let(::LessonId)
            val lesson = lessonId?.let(container.curriculum::lessonOf)

            FingeringRoute(
                settingsRepository = container.settingsRepository,
                audioCapture = container.audioCapture,
                preset = lesson?.let { fingeringPresetOf(it.firstStep) },
                onExerciseFinished = { result ->
                    lessonId?.let { container.lessonProgress.record(it, fingeringOutcomeOf(result)) }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.LEARNING_PATH,
            arguments = listOf(navArgument(PATH_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val path = container.curriculum.pathOf(entry.arguments!!.getString(PATH_ID_ARG)!!)
            if (path == null) {
                navController.popBackStack()
            } else {
                LearningRoute(
                    path = path,
                    progressRepository = container.lessonProgress,
                    isUnlocked = { lesson ->
                        container.curriculum.isUnlocked(
                            lesson.id,
                            container.lessonProgress.progress.value,
                        )
                    },
                    onStartLesson = { id ->
                        container.curriculum.lessonOf(id)?.let { lesson ->
                            navController.navigate(routeForLesson(lesson))
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
 * Where a lesson runs.
 *
 * The single point at which a trainer is registered with the Learning layer: a new trainer adds a
 * branch here and a catalog file of its own, and nothing that already works is touched. Only the
 * first step is used, because a lesson holds exactly one exercise so far.
 */
private fun routeForLesson(lesson: Lesson): String = when (lesson.firstStep.trainerId) {
    TrainerId.FINGERING -> SaxTrainerDestination.fingering(lesson.id)
    TrainerId.LONG_TONES -> SaxTrainerDestination.LONG_TONES
    TrainerId.METRONOME -> SaxTrainerDestination.METRONOME
}

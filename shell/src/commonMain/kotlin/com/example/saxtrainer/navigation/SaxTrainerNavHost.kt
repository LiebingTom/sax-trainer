package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.feature.fingering.FingeringRoute
import com.example.saxtrainer.feature.learning.LearningRoute
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.notequiz.QuizRoute
import com.example.saxtrainer.feature.notetrainer.NoteTrainerRoute
import com.example.saxtrainer.feature.sequence.SequenceRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.AddToLessonsScreen
import com.example.saxtrainer.home.HomeScreen
import com.example.saxtrainer.home.LessonsScreen
import com.example.saxtrainer.home.MyLessonsScreen
import com.example.saxtrainer.home.PracticeScreen
import com.example.saxtrainer.home.learningEntriesOf
import com.example.saxtrainer.learning.SavedSetupRepository
import com.example.saxtrainer.learning.catalog.steps.fingeringOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.fingeringPresetOf
import com.example.saxtrainer.learning.catalog.steps.longToneOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.longTonePresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.sequenceOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.sequencePresetOf
import com.example.saxtrainer.learning.savedLessonIdOf
import com.example.saxtrainer.learning.userUnitsOf

/**
 * Central navigation graph, and the only place that knows both the destinations and the
 * dependencies a feature needs. Feature screens receive callbacks and dependencies, never the
 * [NavHostController], so features stay independent of each other and of the app module.
 *
 * It is also where a lesson meets a trainer: a lesson travels as nothing but its id, the position
 * of the step being run and whether it is being edited, and this graph turns those into the
 * trainer's own configuration and turns the trainer's own result back into lesson progress. Neither
 * side knows the other exists, which is what lets one lesson run several trainers in turn - and
 * what lets a practice the player saved be a lesson like any other.
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
    val book by container.userLessons.book.collectAsStateWithLifecycle()

    // One curriculum for everything the app can teach, whoever wrote it. Built here rather than
    // held in the container because the player's half of it changes while the app is running, and
    // a Curriculum is a value that checks itself when it is made.
    val curriculum = remember(book) {
        Curriculum(units = container.curriculum.units + userUnitsOf(book))
    }

    NavHost(
        navController = navController,
        startDestination = SaxTrainerDestination.HOME,
        modifier = modifier,
    ) {
        composable(SaxTrainerDestination.HOME) {
            // Read straight off the settings rather than through a view model of its own: what is
            // starred is one set that every screen showing it has to agree about, and it is already
            // kept where every other preference is.
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            HomeScreen(
                onEntryClick = { route -> navController.navigate(route) },
                favorites = settings.favorites,
                onToggleFavorite = container::star,
            )
        }
        composable(SaxTrainerDestination.PRACTICE) {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            PracticeScreen(
                onEntryClick = { route -> navController.navigate(route) },
                favorites = settings.favorites,
                onToggleFavorite = container::star,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SaxTrainerDestination.LESSONS) {
            LessonsScreen(
                onEntryClick = { route -> navController.navigate(route) },
                units = learningEntriesOf(container.curriculum),
                onBack = { navController.popBackStack() },
            )
        }
        composable(SaxTrainerDestination.MY_LESSONS) {
            MyLessonsScreen(
                book = book,
                onWrite = container.userLessons::write,
                onStart = { id -> navController.startLesson(container, curriculum, id) },
                onEdit = { id -> navController.editLesson(curriculum, id) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.ADD_TO_LESSONS,
            arguments = listOf(navArgument(EXERCISE_ARG) { type = NavType.StringType }),
        ) { entry ->
            val name = entry.arguments?.read { getStringOrNull(EXERCISE_ARG) }
            val exercise = Exercise.entries.firstOrNull { it.name == name }
            if (exercise == null) {
                navController.popBackStack()
            } else {
                AddToLessonsScreen(
                    book = book,
                    setup = container.exerciseSetups.setupOf(exercise),
                    trainerId = trainerFor(exercise).name,
                    onSave = { updated ->
                        container.userLessons.write(updated)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(SaxTrainerDestination.METRONOME) {
            MetronomeRoute(
                newMetronome = container.newMetronome,
                exerciseSetups = container.exerciseSetups,
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.NOTE_QUIZ_PATTERN,
            container = container,
            curriculum = curriculum,
            navController = navController,
        ) { step ->
            QuizRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                preset = step.stepOf(::noteQuizPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.NOTE_QUIZ),
                onExerciseFinished = { result -> step.finish(noteQuizOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.FINGERING_PATTERN,
            container = container,
            curriculum = curriculum,
            navController = navController,
        ) { step ->
            // No lesson argument is the freely configurable Practice entry, which is what every
            // default in FingeringRoute describes.
            FingeringRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = step.stepOf(::fingeringPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.FINGERING),
                onExerciseFinished = { result -> step.finish(fingeringOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.NOTE_TRAINER_PATTERN,
            container = container,
            curriculum = curriculum,
            navController = navController,
        ) { step ->
            NoteTrainerRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = step.stepOf(::noteTrainerPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.NOTE_TRAINER),
                onExerciseFinished = { result -> step.finish(noteTrainerOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.SEQUENCE_PATTERN,
            container = container,
            curriculum = curriculum,
            navController = navController,
        ) { step ->
            SequenceRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                preset = step.stepOf(::sequencePresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.SEQUENCE),
                onExerciseFinished = { result -> step.finish(sequenceOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.LONG_TONES_PATTERN,
            container = container,
            curriculum = curriculum,
            navController = navController,
        ) { step ->
            LongToneRoute(
                audioCapture = container.audioCapture,
                newMetronome = container.newMetronome,
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                preset = step.stepOf(::longTonePresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.LONG_TONES),
                onExerciseFinished = { result -> step.finish(longToneOutcomeOf(result)) },
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
                    onStartLesson = { id -> navController.startLesson(container, curriculum, id) },
                    onEditLesson = { id -> navController.editLesson(curriculum, id) },
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
 * Stars or unstars one practice tool.
 *
 * The one place a [TrainerId] becomes a stored name. `:core:settings` keeps a set of names and is
 * told nothing about what a trainer is; deciding what a trainer is called is this module's job,
 * exactly as deciding where one lives is.
 */
private fun AppContainer.star(trainerId: TrainerId, favorite: Boolean) {
    settingsRepository.setFavorite(trainerId.name, favorite)
}

/** Which trainer an exercise's setup belongs to. The other half of [Exercise]'s own naming. */
private fun trainerFor(exercise: Exercise): TrainerId = when (exercise) {
    Exercise.NOTE_TRAINER -> TrainerId.NOTE_TRAINER
    Exercise.FINGERING -> TrainerId.FINGERING
    Exercise.LONG_TONES -> TrainerId.LONG_TONES
    Exercise.NOTE_QUIZ -> TrainerId.NOTE_QUIZ
    Exercise.SEQUENCE -> TrainerId.SEQUENCE
    Exercise.METRONOME -> TrainerId.METRONOME
}

/** Runs a lesson from its first step, whether the catalog wrote it or the player saved it. */
private fun NavHostController.startLesson(
    container: AppContainer,
    curriculum: Curriculum,
    id: LessonId,
) {
    val lesson = curriculum.lessonOf(id) ?: return
    // Starting a lesson starts it from the top: what a half-finished attempt had scored is not
    // worth carrying into a new one.
    container.lessonRuns.clear(id)
    routeForStep(lesson, stepIndex = 0)?.let(::navigate)
}

/**
 * Opens a lesson's configuration on the trainer's own setup screen.
 *
 * The first step of it: a lesson of several exercises is edited from the front, which is the only
 * answer that does not invent a screen for choosing which step somebody meant.
 */
private fun NavHostController.editLesson(curriculum: Curriculum, id: LessonId) {
    val lesson = curriculum.lessonOf(id) ?: return
    routeForStep(lesson, stepIndex = 0, edit = true)?.let(::navigate)
}

/**
 * What one trainer destination needs to know about the lesson step it is running, if any.
 *
 * Gathered into one value rather than passed as four parameters because the interesting part is
 * that they go together: which lesson, which step, whether it is being edited, and therefore which
 * store its choices belong in.
 */
private class LessonStepScope(
    val lesson: Lesson?,
    val stepIndex: Int,
    val editing: Boolean,
    /**
     * Where the trainer reads its setup from and writes every change back to.
     *
     * The app's own store for an ordinary practice; the saved lesson itself while one is being
     * edited, so editing is saving; and a store that outlives nothing while a built-in lesson is
     * being edited, so trying somebody else's lesson your own way changes neither the lesson nor
     * what your own trainer opens on next time.
     */
    val exerciseSetups: ExerciseSetupRepository,
    val finish: (ExerciseOutcome) -> Unit,
) {
    /** The step being run, as one trainer's own configuration. */
    fun <T> stepOf(presetOf: (LessonStep) -> T?): T? =
        lesson?.stepAt(stepIndex)?.let(presetOf)

    /**
     * Where "Add to your lessons" goes, or null where it has no business being offered: a trainer
     * that was handed a lesson is running somebody's exercise rather than one being made up.
     */
    fun addToLessons(navController: NavHostController, exercise: Exercise): (() -> Unit)? =
        if (lesson != null) {
            null
        } else {
            { navController.navigate(SaxTrainerDestination.addToLessons(exercise)) }
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
    curriculum: Curriculum,
    navController: NavHostController,
    content: @Composable (LessonStepScope) -> Unit,
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
            navArgument(EDIT_ARG) {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) { entry ->
        val arguments = entry.arguments
        val lessonId = arguments?.read { getStringOrNull(LESSON_ARG) }?.let(::LessonId)
        val lesson = lessonId?.let(curriculum::lessonOf)
        val stepIndex = arguments?.read { getIntOrNull(STEP_ARG) } ?: 0
        val editing = arguments?.read { getBooleanOrNull(EDIT_ARG) } ?: false
        val currentRoute = entry.destination.route ?: pattern

        val setups = remember(lessonId, editing) {
            when {
                !editing -> container.exerciseSetups
                // A practice the player saved is theirs to change, and changing it is all that
                // saving it again means.
                lessonId != null && savedLessonIdOf(lessonId) != null ->
                    SavedSetupRepository(container.userLessons, savedLessonIdOf(lessonId)!!)
                // A built-in lesson tried somebody's own way: nothing about it is kept.
                else -> InMemoryExerciseSetupRepository()
            }
        }

        content(
            LessonStepScope(
                lesson = lesson,
                stepIndex = stepIndex,
                editing = editing,
                exerciseSetups = setups,
            ) { outcome ->
                // An edited lesson is a try rather than an attempt: it was not the lesson as it is
                // written, so it says nothing about whether the lesson was done.
                if (!editing && lessonId != null && lesson != null) {
                    navController.finishStep(
                        container = container,
                        lesson = lesson,
                        lessonId = lessonId,
                        stepIndex = stepIndex,
                        outcome = outcome,
                        currentRoute = currentRoute,
                    )
                }
            },
        )
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
private fun routeForStep(lesson: Lesson, stepIndex: Int, edit: Boolean = false): String? =
    when (lesson.stepAt(stepIndex)?.trainerId) {
        TrainerId.NOTE_QUIZ -> SaxTrainerDestination.noteQuiz(lesson.id, stepIndex, edit)
        TrainerId.FINGERING -> SaxTrainerDestination.fingering(lesson.id, stepIndex, edit)
        TrainerId.NOTE_TRAINER -> SaxTrainerDestination.noteTrainer(lesson.id, stepIndex, edit)
        TrainerId.SEQUENCE -> SaxTrainerDestination.sequence(lesson.id, stepIndex, edit)
        TrainerId.LONG_TONES -> SaxTrainerDestination.longTones(lesson.id, stepIndex, edit)
        // A metronome has nothing to configure and nothing to report, so it is the one step that
        // is simply opened.
        TrainerId.METRONOME -> SaxTrainerDestination.METRONOME
        null -> null
    }

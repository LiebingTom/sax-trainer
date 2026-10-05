package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.saxtrainer.core.designsystem.component.LocalScreenTrail
import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.ScopedExerciseSetups
import com.example.saxtrainer.feature.learning.LearningRoute
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.lexicon.LexiconRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.metronome.MetronomeSetupRoute
import com.example.saxtrainer.feature.notequiz.QuizRoute
import com.example.saxtrainer.feature.notetrainer.NoteTrainerRoute
import com.example.saxtrainer.feature.phrasequiz.PhraseQuizRoute
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerRoute
import com.example.saxtrainer.feature.sequence.SequenceRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.AddToLessonsScreen
import com.example.saxtrainer.home.HomeScreen
import com.example.saxtrainer.home.LessonsScreen
import com.example.saxtrainer.home.MyLessonGroupScreen
import com.example.saxtrainer.home.MyLessonsScreen
import com.example.saxtrainer.home.PracticeScreen
import com.example.saxtrainer.home.learningEntriesOf
import com.example.saxtrainer.home.practiceIconOf
import com.example.saxtrainer.learning.catalog.steps.longToneOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.longTonePresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.phraseQuizOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.phraseQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.sequenceOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.sequencePresetOf
import com.example.saxtrainer.learning.savedLessonIdOf
import com.example.saxtrainer.learning.lessonStepOf
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

    // A lesson opened to be edited is played the player's way without being changed: the edits
    // live here, one setup per lesson step, and only "Add to your lessons" turns them into
    // something kept. Held by the graph rather than by the trainer's destination, so the lesson's
    // own card starts with them too until Home is reached again; saveable, so they outlast the app
    // being closed in the background while the phone is locked.
    val lessonEdits = rememberSaveable(saver = LessonEditsSaver) { ScopedExerciseSetups() }

    NavHost(
        navController = navController,
        startDestination = SaxTrainerDestination.HOME,
        modifier = modifier,
    ) {
        composable(SaxTrainerDestination.HOME) {
            // Back home is leaving the lessons: edits made to try a lesson differently end here.
            LaunchedEffect(Unit) { lessonEdits.clear() }
            // Read straight off the settings rather than through a view model of its own: what is
            // starred is one set that every screen showing it has to agree about, and it is already
            // kept where every other preference is.
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            HomeScreen(
                onEntryClick = { route -> navController.navigate(route) },
                favorites = settings.favorites,
            )
        }
        composable(SaxTrainerDestination.PRACTICE) {
            PracticeScreen(
                onEntryClick = { route -> navController.navigate(route) },
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
                onOpenGroup = { id -> navController.navigate(SaxTrainerDestination.myLessonGroup(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SaxTrainerDestination.MY_LESSON_GROUP,
            arguments = listOf(navArgument(GROUP_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val group = entry.arguments
                ?.read { getStringOrNull(GROUP_ID_ARG) }
                ?.let(book::groupOf)
            if (group == null) {
                navController.popBackStack()
            } else {
                MyLessonGroupScreen(
                    book = book,
                    group = group,
                    onWrite = container.userLessons::write,
                    onStart = { id -> navController.startLesson(container, curriculum, id) },
                    onEdit = { id -> navController.editLesson(curriculum, id) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = SaxTrainerDestination.ADD_TO_LESSONS,
            arguments = listOf(
                navArgument(EXERCISE_ARG) { type = NavType.StringType },
                navArgument(EDITED_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val name = entry.arguments?.read { getStringOrNull(EXERCISE_ARG) }
            val edited = entry.arguments?.read { getBooleanOrNull(EDITED_ARG) } ?: false
            val exercise = Exercise.entries.firstOrNull { it.name == name }
            if (exercise == null) {
                navController.popBackStack()
            } else {
                AddToLessonsScreen(
                    book = book,
                    setup = (if (edited) lessonEdits.latestByExercise else container.exerciseSetups).setupOf(exercise),
                    trainerId = trainerFor(exercise).name,
                    onSave = { updated ->
                        container.userLessons.write(updated)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(SaxTrainerDestination.METRONOME_SETUP) {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            MetronomeSetupRoute(
                exerciseSetups = container.exerciseSetups,
                isFavorite = TrainerId.METRONOME.name in settings.favorites,
                onToggleFavorite = { wanted -> container.star(TrainerId.METRONOME, wanted) },
                onStart = { navController.navigate(SaxTrainerDestination.METRONOME) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SaxTrainerDestination.FINGERING_LEXICON) {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            LexiconRoute(
                settingsRepository = container.settingsRepository,
                isFavorite = TrainerId.FINGERING_LEXICON.name in settings.favorites,
                onToggleFavorite = { wanted -> container.star(TrainerId.FINGERING_LEXICON, wanted) },
                onBack = { navController.popBackStack() },
            )
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
            lessonEdits = lessonEdits,
            navController = navController,
        ) { step ->
            QuizRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                preset = step.stepOf(::noteQuizPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.NOTE_QUIZ),
                isFavorite = step.isFavorite(TrainerId.NOTE_QUIZ),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.NOTE_QUIZ),
                onExerciseFinished = { result -> step.finish(noteQuizOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.NOTE_TRAINER_PATTERN,
            container = container,
            curriculum = curriculum,
            lessonEdits = lessonEdits,
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
                isFavorite = step.isFavorite(TrainerId.NOTE_TRAINER),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.NOTE_TRAINER),
                onExerciseFinished = { result -> step.finish(noteTrainerOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.SEQUENCE_PATTERN,
            container = container,
            curriculum = curriculum,
            lessonEdits = lessonEdits,
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
                isFavorite = step.isFavorite(TrainerId.SEQUENCE),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.SEQUENCE),
                onExerciseFinished = { result -> step.finish(sequenceOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.LONG_TONES_PATTERN,
            container = container,
            curriculum = curriculum,
            lessonEdits = lessonEdits,
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
                isFavorite = step.isFavorite(TrainerId.LONG_TONES),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.LONG_TONES),
                onExerciseFinished = { result -> step.finish(longToneOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.PHRASE_QUIZ_PATTERN,
            container = container,
            curriculum = curriculum,
            lessonEdits = lessonEdits,
            navController = navController,
        ) { step ->
            PhraseQuizRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                phraseCatalog = container.phraseCatalog,
                patternCatalog = container.patternCatalog,
                preset = step.stepOf(::phraseQuizPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.PHRASE_QUIZ),
                isFavorite = step.isFavorite(TrainerId.PHRASE_QUIZ),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.PHRASE_QUIZ),
                onExerciseFinished = { result -> step.finish(phraseQuizOutcomeOf(result)) },
                onBack = { navController.popBackStack() },
            )
        }
        lessonStep(
            pattern = SaxTrainerDestination.PHRASE_TRAINER_PATTERN,
            container = container,
            curriculum = curriculum,
            lessonEdits = lessonEdits,
            navController = navController,
        ) { step ->
            PhraseTrainerRoute(
                settingsRepository = container.settingsRepository,
                exerciseSetups = step.exerciseSetups,
                audioCapture = container.audioCapture,
                phraseCatalog = container.phraseCatalog,
                patternCatalog = container.patternCatalog,
                preset = step.stepOf(::phraseTrainerPresetOf)
                    ?.copy(autoStart = !step.editing, editable = step.editing),
                onAddToLessons = step.addToLessons(navController, Exercise.PHRASE_TRAINER),
                isFavorite = step.isFavorite(TrainerId.PHRASE_TRAINER),
                onToggleFavorite = step.toggleFavorite(container, TrainerId.PHRASE_TRAINER),
                onExerciseFinished = { result -> step.finish(phraseTrainerOutcomeOf(result)) },
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
                    iconOf = { lesson -> practiceIconOf(lesson.firstStep.trainerId) },
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
 * Stars or unstars one practice tool, from the star in its title bar.
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
    Exercise.LONG_TONES -> TrainerId.LONG_TONES
    Exercise.NOTE_QUIZ -> TrainerId.NOTE_QUIZ
    Exercise.SEQUENCE -> TrainerId.SEQUENCE
    Exercise.METRONOME -> TrainerId.METRONOME
    Exercise.PHRASE_QUIZ -> TrainerId.PHRASE_QUIZ
    Exercise.PHRASE_TRAINER -> TrainerId.PHRASE_TRAINER
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
    /** The step being run: as the lesson writes it, or as the player last edited it. */
    val step: LessonStep?,
    val stepIndex: Int,
    val editing: Boolean,
    /**
     * Where the trainer reads its setup from and writes every change back to.
     *
     * The app's own store for an ordinary practice or a lesson being played. A lesson being edited,
     * saved or built in, gets a store of edits instead: it is played the player's way before it is
     * started, and changes neither the lesson nor what their own trainer opens on next time. Keeping
     * it is "Add to your lessons", as for any other practice.
     */
    val exerciseSetups: ExerciseSetupRepository,
    /** The names of the starred practices, as the settings keep them. */
    val favorites: Set<String>,
    val finish: (ExerciseOutcome) -> Unit,
) {
    /** The step being run, as one trainer's own configuration. */
    fun <T> stepOf(presetOf: (LessonStep) -> T?): T? =
        step?.let(presetOf)

    /**
     * Where "Add to your lessons" goes, or null where it has no business being offered: a lesson
     * being played is somebody's exercise rather than one being made up. A lesson being edited is
     * one being made up, and is kept from its edits.
     */
    fun addToLessons(navController: NavHostController, exercise: Exercise): (() -> Unit)? = when {
        lesson == null -> {
            { navController.navigate(SaxTrainerDestination.addToLessons(exercise)) }
        }
        editing -> {
            { navController.navigate(SaxTrainerDestination.addToLessons(exercise, edited = true)) }
        }
        else -> null
    }

    fun isFavorite(trainer: TrainerId): Boolean = trainer.name in favorites

    /** How the star in the title bar changes it, or null inside a lesson - for the same reason. */
    fun toggleFavorite(container: AppContainer, trainer: TrainerId): ((Boolean) -> Unit)? =
        if (lesson != null) null else { wanted -> container.star(trainer, wanted) }
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
    lessonEdits: ScopedExerciseSetups,
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
        val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

        // The step as the player last edited it, where they did: the lesson's card starts with
        // those edits as well, but a run of them is a try rather than an attempt, as in editing.
        val scope = lessonId?.let { editScopeOf(it, stepIndex) }
        val written = lesson?.stepAt(stepIndex)
        val edited = scope?.let(lessonEdits::setupOf)?.takeUnless { it.isEmpty }
            ?.let { setup -> written?.let { lessonStepOf(it.trainerId, setup)?.copy(title = it.title) } }
            ?.takeIf { it != written }
        val setups = if (editing && scope != null) lessonEdits.repositoryFor(scope) else container.exerciseSetups

        // Every screen of the step says which lesson it belongs to; the trainer draws it without
        // knowing it is in one.
        val trail = lesson?.let { trailOf(curriculum, it) }.orEmpty()
        CompositionLocalProvider(LocalScreenTrail provides trail) {
            content(
                LessonStepScope(
                    lesson = lesson,
                    step = edited ?: written,
                    stepIndex = stepIndex,
                    editing = editing,
                    exerciseSetups = setups,
                    favorites = settings.favorites,
                ) { outcome ->
                    // An edited lesson is a try rather than an attempt: it was not the lesson as it
                    // is written, so it says nothing about whether the lesson was done.
                    if (!editing && edited == null && lessonId != null && lesson != null) {
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
}

/**
 * Where a lesson sits, as its screens name it: `My Lessons / Scales / Test2` for one the player
 * saved, `Lessons / C major / First scale` for one of the catalog's.
 */
private fun trailOf(curriculum: Curriculum, lesson: Lesson): List<String> = listOfNotNull(
    if (savedLessonIdOf(lesson.id) != null) "My Lessons" else "Lessons",
    curriculum.unitOfLesson(lesson.id)?.title,
    lesson.title,
)

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
        TrainerId.NOTE_TRAINER -> SaxTrainerDestination.noteTrainer(lesson.id, stepIndex, edit)
        TrainerId.SEQUENCE -> SaxTrainerDestination.sequence(lesson.id, stepIndex, edit)
        TrainerId.LONG_TONES -> SaxTrainerDestination.longTones(lesson.id, stepIndex, edit)
        TrainerId.PHRASE_QUIZ -> SaxTrainerDestination.phraseQuiz(lesson.id, stepIndex, edit)
        TrainerId.PHRASE_TRAINER -> SaxTrainerDestination.phraseTrainer(lesson.id, stepIndex, edit)
        // A metronome has nothing to configure and nothing to report, so it is the one step that
        // is simply opened.
        TrainerId.METRONOME -> SaxTrainerDestination.METRONOME
        TrainerId.FINGERING_LEXICON -> SaxTrainerDestination.FINGERING_LEXICON
        null -> null
    }

/** Where the edits to one step of one lesson are kept. */
private fun editScopeOf(lessonId: LessonId, stepIndex: Int): String = "${lessonId.value}#$stepIndex"

/** Carries the lesson edits across the app being recreated, as the platform keeps saved state. */
private val LessonEditsSaver: Saver<ScopedExerciseSetups, ArrayList<String>> = Saver(
    save = { it.save() },
    restore = { ScopedExerciseSetups.restore(it) },
)

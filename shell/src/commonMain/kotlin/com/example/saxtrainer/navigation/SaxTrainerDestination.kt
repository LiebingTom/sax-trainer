package com.example.saxtrainer.navigation

import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.settings.Exercise

/** Every navigation target of the app. Adding a training tool means adding one entry here. */
object SaxTrainerDestination {
    const val HOME = "home"
    const val METRONOME = "metronome"
    const val LONG_TONES = "longtones"
    const val NOTE_QUIZ = "notequiz"
    const val FINGERING = "fingering"
    const val NOTE_TRAINER = "notetrainer"
    const val SEQUENCE = "sequence"
    const val SETTINGS = "settings"

    /** The whole catalogue of practice tools, and the only place a tool can be starred. */
    const val PRACTICE = "practice"

    /** The curriculum's units. One lands on [LEARNING_UNIT] for the lessons inside it. */
    const val LESSONS = "lessons"

    /** The practices the player saved, in the groups they put them in. */
    const val MY_LESSONS = "mylessons"

    /** Naming and filing the practice one trainer is currently set up for. */
    const val ADD_TO_LESSONS = "addlesson/{$EXERCISE_ARG}"

    /** The lessons of one unit, e.g. `learning/octave`. */
    const val LEARNING_UNIT = "learning/{$UNIT_ID_ARG}"

    /**
     * The fingering trainer, optionally carrying the lesson step that asked for it.
     *
     * Only the lesson's id, the step's position and whether it is being edited travel in the route:
     * the app resolves them back to a configuration from its own catalog, so nothing has to be
     * serialised and the trainer's own types never appear in a URL. Keeping the step in the route
     * rather than in a coordinator is what lets a lesson of several exercises survive rotation and
     * the back stack.
     *
     * Nothing in this layer escapes anything, so only ids the app generates may travel here. A
     * lesson the player saved is reached by its generated id; the name they typed never is.
     *
     * Navigating to plain [FINGERING] matches this pattern with no lesson, which is the freely
     * configurable Practice entry.
     */
    const val FINGERING_PATTERN = "$FINGERING?$LESSON_QUERY"

    /** The other preset-carrying trainers, on the same terms as [FINGERING_PATTERN]. */
    const val NOTE_QUIZ_PATTERN = "$NOTE_QUIZ?$LESSON_QUERY"
    const val NOTE_TRAINER_PATTERN = "$NOTE_TRAINER?$LESSON_QUERY"
    const val SEQUENCE_PATTERN = "$SEQUENCE?$LESSON_QUERY"
    const val LONG_TONES_PATTERN = "$LONG_TONES?$LESSON_QUERY"

    fun noteQuiz(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(NOTE_QUIZ, lessonId, stepIndex, edit)

    fun fingering(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(FINGERING, lessonId, stepIndex, edit)

    fun noteTrainer(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(NOTE_TRAINER, lessonId, stepIndex, edit)

    fun sequence(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(SEQUENCE, lessonId, stepIndex, edit)

    fun longTones(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(LONG_TONES, lessonId, stepIndex, edit)

    fun learningUnit(unitId: String): String = "learning/$unitId"

    fun addToLessons(exercise: Exercise): String = "addlesson/${exercise.name}"

    private fun withLesson(
        route: String,
        lessonId: LessonId?,
        stepIndex: Int,
        edit: Boolean,
    ): String = if (lessonId == null) {
        route
    } else {
        "$route?$LESSON_ARG=${lessonId.value}&$STEP_ARG=$stepIndex&$EDIT_ARG=$edit"
    }
}

const val UNIT_ID_ARG = "unitId"
const val LESSON_ARG = "lesson"
const val STEP_ARG = "step"
const val EXERCISE_ARG = "exercise"

/**
 * Whether the trainer opens on its setup screen filled in with the lesson's configuration, rather
 * than on the lesson itself.
 *
 * A way of opening rather than a different exercise, which is why it is one more argument on the
 * same route instead of a route of its own.
 */
const val EDIT_ARG = "edit"

private const val LESSON_QUERY =
    "$LESSON_ARG={$LESSON_ARG}&$STEP_ARG={$STEP_ARG}&$EDIT_ARG={$EDIT_ARG}"

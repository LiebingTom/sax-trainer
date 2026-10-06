package com.example.saxtrainer.navigation

import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.settings.Exercise

/** Every navigation target of the app. Adding a training tool means adding one entry here. */
object SaxTrainerDestination {
    const val HOME = "home"
    const val METRONOME = "metronome"

    /** Where the metronome is configured - the time signature it clicks in - and starred. */
    const val METRONOME_SETUP = "metronome/setup"
    const val LONG_TONES = "longtones"
    const val NOTE_QUIZ = "notequiz"
    const val NOTE_TRAINER = "notetrainer"
    const val PHRASE_QUIZ = "phrasequiz"
    const val PHRASE_TRAINER = "phrasetrainer"
    const val SEQUENCE = "sequence"
    const val SETTINGS = "settings"

    /** Every note's grips, looked up rather than practised. */
    const val FINGERING_LEXICON = "lexicon"

    /** The reeds in rotation, and a session to time on one. */
    const val REED_LOGBOOK = "reeds"

    /** Every reed there has been. */
    const val REED_HISTORY = "reeds-history"

    /** Adding a reed. */
    const val REED_ADD = "reeds-add"

    /** One reed and its sessions, e.g. `reed/7f3c…` - by the reed's generated id, never its number. */
    const val REED_DETAIL = "reed/{$REED_ID_ARG}"

    /** Describing one reed again. */
    const val REED_EDIT = "reed/{$REED_ID_ARG}/edit"

    fun reedDetail(reedId: String): String = "reed/$reedId"

    fun reedEdit(reedId: String): String = "reed/$reedId/edit"

    /** The whole catalogue of practice tools, and the only place a tool can be starred. */
    const val PRACTICE = "practice"

    /** The curriculum's units. One lands on [LEARNING_UNIT] for the lessons inside it. */
    const val LESSONS = "lessons"

    /** The practices the player saved, in the groups they put them in. */
    const val MY_LESSONS = "mylessons"

    /** The practices saved in one group of My Lessons, e.g. `mylessons/g2`. */
    const val MY_LESSON_GROUP = "mylessons/{$GROUP_ID_ARG}"

    /**
     * Naming and filing the practice one trainer is currently set up for - as a practice of its own,
     * or as a lesson being edited before it is played ([EDITED_ARG]).
     */
    const val ADD_TO_LESSONS = "addlesson/{$EXERCISE_ARG}?$EDITED_ARG={$EDITED_ARG}"

    /** The lessons of one unit, e.g. `learning/octave`. */
    const val LEARNING_UNIT = "learning/{$UNIT_ID_ARG}"

    /**
     * The note quiz, optionally carrying the lesson step that asked for it.
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
     * Navigating to plain [NOTE_QUIZ] matches this pattern with no lesson, which is the freely
     * configurable Practice entry.
     */
    const val NOTE_QUIZ_PATTERN = "$NOTE_QUIZ?$LESSON_QUERY"

    /** The other preset-carrying trainers, on the same terms as [NOTE_QUIZ_PATTERN]. */
    const val NOTE_TRAINER_PATTERN = "$NOTE_TRAINER?$LESSON_QUERY"
    const val SEQUENCE_PATTERN = "$SEQUENCE?$LESSON_QUERY"
    const val LONG_TONES_PATTERN = "$LONG_TONES?$LESSON_QUERY"
    const val PHRASE_QUIZ_PATTERN = "$PHRASE_QUIZ?$LESSON_QUERY"
    const val PHRASE_TRAINER_PATTERN = "$PHRASE_TRAINER?$LESSON_QUERY"

    fun noteQuiz(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(NOTE_QUIZ, lessonId, stepIndex, edit)

    fun noteTrainer(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(NOTE_TRAINER, lessonId, stepIndex, edit)

    fun sequence(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(SEQUENCE, lessonId, stepIndex, edit)

    fun longTones(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(LONG_TONES, lessonId, stepIndex, edit)

    fun phraseQuiz(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(PHRASE_QUIZ, lessonId, stepIndex, edit)

    fun phraseTrainer(lessonId: LessonId? = null, stepIndex: Int = 0, edit: Boolean = false): String =
        withLesson(PHRASE_TRAINER, lessonId, stepIndex, edit)

    fun learningUnit(unitId: String): String = "learning/$unitId"

    /** Only generated group ids travel here, never the name the player typed. */
    fun myLessonGroup(groupId: String): String = "mylessons/$groupId"

    fun addToLessons(exercise: Exercise, edited: Boolean = false): String =
        "addlesson/${exercise.name}?$EDITED_ARG=$edited"

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
const val GROUP_ID_ARG = "groupId"
const val REED_ID_ARG = "reedId"
const val LESSON_ARG = "lesson"
const val STEP_ARG = "step"
const val EXERCISE_ARG = "exercise"

/** Whether the setup to keep is a lesson being edited rather than the trainer's own. */
const val EDITED_ARG = "edited"

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

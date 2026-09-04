package com.example.saxtrainer.navigation

import com.example.saxtrainer.core.learning.LessonId

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

    /** The lessons of one unit, e.g. `learning/octave`. */
    const val LEARNING_UNIT = "learning/{$UNIT_ID_ARG}"

    /**
     * The fingering trainer, optionally carrying the lesson step that asked for it.
     *
     * Only the lesson's id and the step's position travel in the route: the app resolves them back
     * to a configuration from its own catalog, so nothing has to be serialised and the trainer's own
     * types never appear in a URL. Keeping the step in the route rather than in a coordinator is
     * what lets a lesson of several exercises survive rotation and the back stack.
     *
     * Navigating to plain [FINGERING] matches this pattern with no lesson, which is the freely
     * configurable Practice entry.
     */
    const val FINGERING_PATTERN = "$FINGERING?$LESSON_ARG={$LESSON_ARG}&$STEP_ARG={$STEP_ARG}"

    /** The other preset-carrying trainers, on the same terms as [FINGERING_PATTERN]. */
    const val NOTE_QUIZ_PATTERN = "$NOTE_QUIZ?$LESSON_ARG={$LESSON_ARG}&$STEP_ARG={$STEP_ARG}"
    const val NOTE_TRAINER_PATTERN =
        "$NOTE_TRAINER?$LESSON_ARG={$LESSON_ARG}&$STEP_ARG={$STEP_ARG}"
    const val SEQUENCE_PATTERN = "$SEQUENCE?$LESSON_ARG={$LESSON_ARG}&$STEP_ARG={$STEP_ARG}"

    fun noteQuiz(lessonId: LessonId? = null, stepIndex: Int = 0): String =
        withLesson(NOTE_QUIZ, lessonId, stepIndex)

    fun fingering(lessonId: LessonId? = null, stepIndex: Int = 0): String =
        withLesson(FINGERING, lessonId, stepIndex)

    fun noteTrainer(lessonId: LessonId? = null, stepIndex: Int = 0): String =
        withLesson(NOTE_TRAINER, lessonId, stepIndex)

    fun sequence(lessonId: LessonId? = null, stepIndex: Int = 0): String =
        withLesson(SEQUENCE, lessonId, stepIndex)

    fun learningUnit(unitId: String): String = "learning/$unitId"

    private fun withLesson(route: String, lessonId: LessonId?, stepIndex: Int): String =
        if (lessonId == null) route else "$route?$LESSON_ARG=${lessonId.value}&$STEP_ARG=$stepIndex"
}

const val UNIT_ID_ARG = "unitId"
const val LESSON_ARG = "lesson"
const val STEP_ARG = "step"

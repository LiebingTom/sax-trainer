package com.example.saxtrainer.navigation

import com.example.saxtrainer.core.learning.LessonId

/** Every navigation target of the app. Adding a training tool means adding one entry here. */
object SaxTrainerDestination {
    const val HOME = "home"
    const val METRONOME = "metronome"
    const val LONG_TONES = "longtones"
    const val FINGERING = "fingering"
    const val SETTINGS = "settings"

    /** The lessons of one path, e.g. `learning/fingering`. */
    const val LEARNING_PATH = "learning/{$PATH_ID_ARG}"

    /**
     * The fingering trainer, optionally carrying the lesson that asked for it.
     *
     * Only the lesson's id travels in the route: the app resolves it back to a configuration from
     * its own catalog, so nothing has to be serialised and the trainer's own types never appear in
     * a URL. Navigating to plain [FINGERING] matches this pattern with no lesson, which is the
     * freely configurable Practice entry.
     */
    const val FINGERING_PATTERN = "$FINGERING?$LESSON_ARG={$LESSON_ARG}"

    fun fingering(lessonId: LessonId? = null): String =
        if (lessonId == null) FINGERING else "$FINGERING?$LESSON_ARG=${lessonId.value}"

    fun learningPath(pathId: String): String = "learning/$pathId"
}

const val PATH_ID_ARG = "pathId"
const val LESSON_ARG = "lesson"

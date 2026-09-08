package com.example.saxtrainer.learning

import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.UserLessonRepository
import com.example.saxtrainer.core.settings.withLesson

/**
 * One saved practice, offered to a trainer as if it were that trainer's own last setup.
 *
 * This is what makes editing a saved exercise the same screen as setting one up. A trainer reads
 * the setup it opens on from an [ExerciseSetupRepository] and writes every change back to one; hand
 * it this instead of the app's and both ends land in the saved practice, so editing simply is
 * saving. No trainer learns that a saved lesson exists.
 *
 * Which [Exercise] is asked for is ignored on purpose: this repository holds one practice, and the
 * only trainer that will ever ask is the one that practice belongs to.
 */
class SavedSetupRepository(
    private val userLessons: UserLessonRepository,
    private val lessonId: String,
) : ExerciseSetupRepository {

    override fun setupOf(exercise: Exercise): ExerciseSetup =
        userLessons.book.value.lessonOf(lessonId)?.setup ?: ExerciseSetup.EMPTY

    override fun remember(exercise: Exercise, setup: ExerciseSetup) {
        userLessons.write(userLessons.book.value.withLesson(lessonId, setup))
    }
}

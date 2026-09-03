package com.example.saxtrainer.feature.learning

import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonPath
import com.example.saxtrainer.core.learning.LessonProgressState

/** One lesson as the list draws it. */
data class LessonItem(
    val lesson: Lesson,
    val completed: Boolean,
    val unlocked: Boolean,
    val attempts: Int,
    /** Best fraction played correctly, or null where nothing was ever judged. */
    val bestScore: Float?,
) {
    val id get() = lesson.id
    val title get() = lesson.title
    val subtitle get() = lesson.subtitle
}

/**
 * Everything the lesson list renders.
 *
 * Built from a [LessonPath] and the current progress, which is all this feature ever sees: what a
 * lesson actually runs is carried inside it and never inspected here.
 */
data class LearningUiState(
    val title: String,
    val description: String,
    val lessons: List<LessonItem>,
) {
    val completedCount: Int get() = lessons.count { it.completed }

    val summary: String get() = "$completedCount of ${lessons.size} finished"
}

/** Folds a path and the progress so far into what the screen shows. */
fun learningUiStateOf(
    path: LessonPath,
    progress: LessonProgressState,
    isUnlocked: (Lesson) -> Boolean,
): LearningUiState = LearningUiState(
    title = path.title,
    description = path.description,
    lessons = path.lessons.map { lesson ->
        val lessonProgress = progress.of(lesson.id)
        LessonItem(
            lesson = lesson,
            completed = lessonProgress.completed,
            unlocked = isUnlocked(lesson),
            attempts = lessonProgress.attempts,
            bestScore = lessonProgress.bestScore,
        )
    },
)

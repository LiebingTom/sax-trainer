package com.example.saxtrainer.feature.learning

import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LearningUnit
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

    /** More than one exercise in a sitting; the list says so rather than surprising anybody. */
    val stepCount get() = lesson.stepCount
    val isMultiStep get() = lesson.isMultiStep
}

/**
 * Everything the lesson list renders.
 *
 * Built from a [LearningUnit] and the current progress, which is all this feature ever sees: what a
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

/** Folds a unit and the progress so far into what the screen shows. */
fun learningUiStateOf(
    unit: LearningUnit,
    progress: LessonProgressState,
    isUnlocked: (Lesson) -> Boolean,
): LearningUiState = LearningUiState(
    title = unit.title,
    description = unit.description,
    lessons = unit.lessons.map { lesson ->
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

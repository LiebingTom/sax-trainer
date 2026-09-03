package com.example.saxtrainer.core.learning

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How one attempt at an exercise turned out, in terms the Learning layer understands.
 *
 * Trainers report their own richer results; the app's catalog reduces them to this, so no trainer
 * result type ever reaches this module.
 */
data class ExerciseOutcome(
    /** True when the exercise was played to its end rather than abandoned. */
    val completed: Boolean,
    /** 0..1 where the exercise could judge the playing, null where it could not. */
    val score: Float? = null,
) {
    init {
        require(score == null || score in 0f..1f) { "a score is a fraction, was $score" }
    }
}

/** What has happened to one lesson so far. */
data class LessonProgress(
    val attempts: Int = 0,
    val completed: Boolean = false,
    val bestScore: Float? = null,
) {
    /** Folds one more attempt in, keeping the best score ever reached. */
    fun plus(outcome: ExerciseOutcome): LessonProgress = LessonProgress(
        attempts = attempts + 1,
        completed = completed || outcome.completed,
        bestScore = listOfNotNull(bestScore, outcome.score).maxOrNull(),
    )
}

/** Progress across the whole curriculum. A missing entry simply means "never attempted". */
data class LessonProgressState(
    val byLesson: Map<LessonId, LessonProgress> = emptyMap(),
) {
    fun of(id: LessonId): LessonProgress = byLesson[id] ?: LessonProgress()

    fun isCompleted(id: LessonId): Boolean = byLesson[id]?.completed == true

    fun completedCount(lessons: List<Lesson>): Int = lessons.count { isCompleted(it.id) }
}

/**
 * Where lesson progress lives.
 *
 * Kept as an interface with an in-memory implementation so the Learning layer is complete and
 * testable before anything is written to disk. A persistent implementation belongs beside the
 * existing `SharedPreferencesSettingsRepository`, which already owns the app's stored values;
 * nothing in this module changes when it arrives.
 */
interface LessonProgressRepository {
    val progress: StateFlow<LessonProgressState>

    fun record(id: LessonId, outcome: ExerciseOutcome)
}

/** Progress for this run of the app only. */
class InMemoryLessonProgressRepository(
    initial: LessonProgressState = LessonProgressState(),
) : LessonProgressRepository {

    private val _progress = MutableStateFlow(initial)
    override val progress: StateFlow<LessonProgressState> = _progress.asStateFlow()

    override fun record(id: LessonId, outcome: ExerciseOutcome) {
        val current = _progress.value
        _progress.value = current.copy(
            byLesson = current.byLesson + (id to current.of(id).plus(outcome)),
        )
    }
}

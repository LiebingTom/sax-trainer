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

/**
 * What a whole lesson scored, from what each of its steps did.
 *
 * Every step has to be finished for the lesson to count: an abandoned step means the lesson was not
 * done, whatever the earlier ones managed. Steps that could not judge the playing are left out of
 * the average rather than counted as zero, so a quiz followed by a silent exercise is scored on the
 * quiz alone rather than being halved by something nobody measured.
 */
fun List<ExerciseOutcome>.asLessonOutcome(): ExerciseOutcome = ExerciseOutcome(
    completed = isNotEmpty() && all { it.completed },
    score = mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
)

/**
 * What the steps of the lesson being worked through have scored so far.
 *
 * A lesson is only recorded once, when its last step ends, so the outcomes of the steps before it
 * have to be kept somewhere in the meantime. Separate from [LessonProgressRepository] because this
 * is scaffolding for one sitting rather than something worth remembering.
 */
interface LessonRunRepository {
    fun record(id: LessonId, stepIndex: Int, outcome: ExerciseOutcome)

    fun outcomeOf(id: LessonId): ExerciseOutcome

    fun clear(id: LessonId)
}

/** A lesson run for this run of the app only. */
class InMemoryLessonRunRepository : LessonRunRepository {

    private val steps = mutableMapOf<LessonId, MutableMap<Int, ExerciseOutcome>>()

    override fun record(id: LessonId, stepIndex: Int, outcome: ExerciseOutcome) {
        steps.getOrPut(id) { mutableMapOf() }[stepIndex] = outcome
    }

    override fun outcomeOf(id: LessonId): ExerciseOutcome =
        // Sorted by step index rather than by insertion, because a step may be redone out of
        // order. toSortedMap would do it too, but it is JVM-only.
        steps[id].orEmpty().entries.sortedBy { it.key }.map { it.value }.asLessonOutcome()

    override fun clear(id: LessonId) {
        steps.remove(id)
    }
}

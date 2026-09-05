package com.example.saxtrainer.core.learning

/**
 * Everything the app can teach, as data.
 *
 * The catalog is checked when it is built rather than when a player taps something: a duplicated id
 * or a prerequisite pointing at nothing is a mistake in the lesson data, and failing at startup
 * makes it impossible to ship one unnoticed.
 */
data class Curriculum(val units: List<LearningUnit>) {

    val lessons: List<Lesson> = units.flatMap { it.lessons }

    init {
        val duplicates = lessons.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "duplicate lesson ids: ${duplicates.joinToString { it.value }}"
        }
        require(units.map { it.id }.distinct().size == units.size) { "duplicate learning unit ids" }

        // A prerequisite may only point backwards, which is also what makes a cycle impossible.
        units.forEach { unit ->
            unit.lessons.forEachIndexed { index, lesson ->
                val required = lesson.requires ?: return@forEachIndexed
                val position = unit.lessons.indexOfFirst { it.id == required }
                require(position in 0 until index) {
                    "lesson ${lesson.id.value} requires ${required.value}, " +
                        "which must be an earlier lesson of the same unit"
                }
            }
        }
    }

    fun unitOf(unitId: String): LearningUnit? = units.firstOrNull { it.id == unitId }

    fun lessonOf(id: LessonId): Lesson? = lessons.firstOrNull { it.id == id }

    /** Which unit a lesson belongs to, so finishing one can return to where it was started from. */
    fun unitOfLesson(id: LessonId): LearningUnit? =
        units.firstOrNull { unit -> unit.lessons.any { it.id == id } }

    /**
     * A lesson can be started when it asks for nothing, or when what it asks for is finished. With
     * every prerequisite left null the whole catalog is open, which is how it starts out.
     */
    fun isUnlocked(id: LessonId, progress: LessonProgressState): Boolean {
        val required = lessonOf(id)?.requires ?: return true
        return progress.isCompleted(required)
    }

    /**
     * The part of this curriculum that somewhere with only [trainers] can actually teach.
     *
     * A lesson survives when every one of its steps has a trainer here, and when whatever it
     * requires survived too - a lesson kept behind a prerequisite that was dropped could never be
     * unlocked. A unit left with no lessons goes with them, so nothing empty reaches a screen.
     *
     * This exists because a platform may have fewer trainers than the catalog was written for -
     * the web has no microphone trainers yet - and showing a lesson that cannot be started is
     * worse than not showing it at all. Where every trainer is present the result is this same
     * curriculum.
     */
    fun runnableWith(trainers: Set<TrainerId>): Curriculum {
        val kept = mutableSetOf<LessonId>()
        val units = units.mapNotNull { unit ->
            val lessons = unit.lessons.filter { lesson ->
                val runnable = lesson.steps.all { it.trainerId in trainers } &&
                    (lesson.requires == null || lesson.requires in kept)
                if (runnable) kept += lesson.id
                runnable
            }
            if (lessons.isEmpty()) null else unit.copy(lessons = lessons)
        }
        return Curriculum(units)
    }
}

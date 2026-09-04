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
}

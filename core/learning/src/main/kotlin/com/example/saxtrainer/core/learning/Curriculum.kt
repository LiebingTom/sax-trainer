package com.example.saxtrainer.core.learning

/**
 * Everything the app can teach, as data.
 *
 * The catalog is checked when it is built rather than when a player taps something: a duplicated id
 * or a prerequisite pointing at nothing is a mistake in the lesson data, and failing at startup
 * makes it impossible to ship one unnoticed.
 */
data class Curriculum(val paths: List<LessonPath>) {

    val lessons: List<Lesson> = paths.flatMap { it.lessons }

    init {
        val duplicates = lessons.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "duplicate lesson ids: ${duplicates.joinToString { it.value }}"
        }
        require(paths.map { it.id }.distinct().size == paths.size) { "duplicate lesson path ids" }

        // A prerequisite may only point backwards, which is also what makes a cycle impossible.
        paths.forEach { path ->
            path.lessons.forEachIndexed { index, lesson ->
                val required = lesson.requires ?: return@forEachIndexed
                val position = path.lessons.indexOfFirst { it.id == required }
                require(position in 0 until index) {
                    "lesson ${lesson.id.value} requires ${required.value}, " +
                        "which must be an earlier lesson of the same path"
                }
            }
        }
    }

    fun pathOf(pathId: String): LessonPath? = paths.firstOrNull { it.id == pathId }

    fun lessonOf(id: LessonId): Lesson? = lessons.firstOrNull { it.id == id }

    /**
     * A lesson can be started when it asks for nothing, or when what it asks for is finished. With
     * every prerequisite left null the whole catalog is open, which is how it starts out.
     */
    fun isUnlocked(id: LessonId, progress: LessonProgressState): Boolean {
        val required = lessonOf(id)?.requires ?: return true
        return progress.isCompleted(required)
    }
}

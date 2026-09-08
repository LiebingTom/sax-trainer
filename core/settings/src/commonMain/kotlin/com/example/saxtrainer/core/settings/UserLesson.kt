package com.example.saxtrainer.core.settings

/**
 * A one-level folder of saved exercises - "Week 1", "Note reading".
 *
 * One level and no nesting: a group is a heading the player writes for themselves, and a tree of
 * them would be filing rather than practising.
 */
data class UserLessonGroup(val id: String, val name: String)

/**
 * One practice the player saved: what it is called, which trainer runs it, and how it was set up.
 *
 * [trainerId] is an opaque name, exactly as a starred tool is: this module knows nothing about
 * trainers, and a name it does not recognise is kept rather than dropped, so a lesson saved by a
 * newer version survives being read by an older one.
 *
 * The configuration is an [ExerciseSetup] because that is already what a practice looks like when it
 * is written down. Nothing new is invented to store one.
 */
data class UserLesson(
    val id: String,
    val name: String,
    val groupId: String,
    val trainerId: String,
    val setup: ExerciseSetup,
)

/**
 * Everything the player has saved, in the order they put it in.
 *
 * One value rather than a store with an edit method for each rule: every change below is a function
 * from a book to a book, so the rules can be read and tested without a repository, a platform or a
 * screen. [UserLessonRepository] only ever writes a whole book.
 *
 * There is always at least one group, so a saved exercise never has to answer "and where does this
 * one live?".
 */
data class UserLessonBook(
    val groups: List<UserLessonGroup> = listOf(DEFAULT_GROUP),
    val lessons: List<UserLesson> = emptyList(),
) {
    /** The saved exercises of one group, in the player's order. */
    fun lessonsOf(groupId: String): List<UserLesson> = lessons.filter { it.groupId == groupId }

    fun groupOf(id: String): UserLessonGroup? = groups.firstOrNull { it.id == id }

    fun lessonOf(id: String): UserLesson? = lessons.firstOrNull { it.id == id }

    /**
     * An id nothing in this book uses.
     *
     * Counted from the highest in use rather than from how many there are, so deleting the last
     * lesson and saving another cannot hand out an id something else already answers to. Nothing
     * outside a book ever refers to one of these for longer than a session, so an id freed by a
     * delete and later reused is nobody's problem.
     */
    fun nextLessonId(): String = LESSON_PREFIX + (highestNumber(lessons.map { it.id }, LESSON_PREFIX) + 1)

    fun nextGroupId(): String = GROUP_PREFIX + (highestNumber(groups.map { it.id }, GROUP_PREFIX) + 1)

    companion object {
        /** The group a player has before they have made any of their own. */
        val DEFAULT_GROUP = UserLessonGroup("g0", "My lessons")

        internal const val LESSON_PREFIX = "l"
        internal const val GROUP_PREFIX = "g"

        private fun highestNumber(ids: List<String>, prefix: String): Int = ids
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
            .maxOrNull()
            ?: -1
    }
}

/**
 * Saves one more practice at the end of its group.
 *
 * A blank name is refused rather than stored: the name is the only thing that tells two saved
 * exercises apart on the screen.
 */
fun UserLessonBook.plusLesson(
    name: String,
    groupId: String,
    trainerId: String,
    setup: ExerciseSetup,
): UserLessonBook {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return this
    val group = groupOf(groupId) ?: groups.first()

    return copy(
        lessons = lessons + UserLesson(
            id = nextLessonId(),
            name = cleaned,
            groupId = group.id,
            trainerId = trainerId,
            setup = setup,
        ),
    )
}

/** The same practice, set up differently. This is what editing a saved exercise amounts to. */
fun UserLessonBook.withLesson(id: String, setup: ExerciseSetup): UserLessonBook =
    copy(lessons = lessons.map { if (it.id == id) it.copy(setup = setup) else it })

fun UserLessonBook.renamedLesson(id: String, name: String): UserLessonBook {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return this
    return copy(lessons = lessons.map { if (it.id == id) it.copy(name = cleaned) else it })
}

fun UserLessonBook.minusLesson(id: String): UserLessonBook =
    copy(lessons = lessons.filterNot { it.id == id })

/**
 * Moves one saved exercise [by] places within its own group; -1 is up, +1 is down.
 *
 * Off either end is not a move, and nothing happens rather than the exercise jumping into the group
 * above or below: a group is where the player put it.
 */
fun UserLessonBook.movedLesson(id: String, by: Int): UserLessonBook {
    val lesson = lessonOf(id) ?: return this
    val siblings = lessonsOf(lesson.groupId)
    val from = siblings.indexOf(lesson)
    val to = from + by
    if (to !in siblings.indices) return this

    val reordered = siblings.toMutableList().apply { add(to, removeAt(from)) }
    return copy(lessons = rebuilt(reordered, lesson.groupId))
}

/** A new group at the end. A name already in use is allowed: they are the player's headings. */
fun UserLessonBook.plusGroup(name: String): UserLessonBook {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return this
    return copy(groups = groups + UserLessonGroup(nextGroupId(), cleaned))
}

fun UserLessonBook.renamedGroup(id: String, name: String): UserLessonBook {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return this
    return copy(groups = groups.map { if (it.id == id) it.copy(name = cleaned) else it })
}

/**
 * Removes a group and everything saved in it.
 *
 * The last group cannot go: every saved exercise lives in one, so a book with no groups is a book
 * nothing can be added to. Moving the exercises somewhere else instead would be a decision this has
 * no business taking on the player's behalf.
 */
fun UserLessonBook.minusGroup(id: String): UserLessonBook {
    if (groups.size <= 1) return this
    if (groupOf(id) == null) return this

    return copy(
        groups = groups.filterNot { it.id == id },
        lessons = lessons.filterNot { it.groupId == id },
    )
}

/** Moves a group [by] places; -1 is up, +1 is down. Off either end is not a move. */
fun UserLessonBook.movedGroup(id: String, by: Int): UserLessonBook {
    val group = groupOf(id) ?: return this
    val from = groups.indexOf(group)
    val to = from + by
    if (to !in groups.indices) return this

    return copy(groups = groups.toMutableList().apply { add(to, removeAt(from)) })
}

/**
 * The whole list with one group's part replaced, keeping every other group where it was.
 *
 * Lessons are held in one list rather than one per group, so reordering inside a group has to put
 * the reordered ones back into the places that group already occupied.
 */
private fun UserLessonBook.rebuilt(
    reordered: List<UserLesson>,
    groupId: String,
): List<UserLesson> {
    val remaining = reordered.iterator()
    return lessons.map { if (it.groupId == groupId) remaining.next() else it }
}

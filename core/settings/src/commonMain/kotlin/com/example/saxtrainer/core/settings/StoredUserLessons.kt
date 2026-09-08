package com.example.saxtrainer.core.settings

/**
 * What the player's saved practices are stored under and how they are written down.
 *
 * The counterpart of [StoredExerciseSetup] for [UserLessonBook], and shared for the same reason:
 * storage is per platform, the format must not be. One stored string for the whole book keeps both
 * platforms to a single get and a single put, and keeps the order of the groups and of the
 * exercises inside them where it belongs - in the file, as the order the lines are in.
 *
 * The format is one record per line, its fields separated by `|`:
 *
 * ```
 * G|g0|My lessons
 * L|l3|g0|NOTE_TRAINER|Ledger lines, slowly
 * S|custom_notes=74,76,77
 * ```
 *
 * A setup line belongs to the exercise above it, and is otherwise exactly a line of
 * [StoredExerciseSetup]'s own format - so a value may hold an `=`, since that line is split at its
 * first one. Both directions are total: a name that could not survive the round trip is cleaned
 * rather than dropping the exercise it belongs to, and a line that does not parse - including a
 * record this version has never heard of - is skipped, so a book written by another version reads
 * as the parts this version does understand.
 */
internal object StoredUserLessons {

    /** One key for the whole book, kept clear of [StoredSettings]' own names. */
    const val KEY = "user_lessons"

    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "|"

    private const val GROUP = "G"
    private const val LESSON = "L"
    private const val SETUP = "S"

    fun encode(book: UserLessonBook): String {
        val lines = mutableListOf<String>()
        book.groups.forEach { group ->
            lines += listOf(GROUP, group.id, group.name.storable()).joinToString(FIELD_SEPARATOR)
        }
        book.lessons.forEach { lesson ->
            lines += listOf(
                LESSON,
                lesson.id,
                lesson.groupId,
                lesson.trainerId,
                lesson.name.storable(),
            ).joinToString(FIELD_SEPARATOR)
            lines += StoredExerciseSetup.encode(lesson.setup)
                .split(LINE_SEPARATOR)
                .filter { it.isNotEmpty() }
                .map { "$SETUP$FIELD_SEPARATOR$it" }
        }
        return lines.joinToString(LINE_SEPARATOR)
    }

    fun decode(stored: String?): UserLessonBook {
        if (stored.isNullOrEmpty()) return UserLessonBook()

        val groups = mutableListOf<UserLessonGroup>()
        val lessons = mutableListOf<UserLesson>()

        // The setup lines of an exercise follow it, so the exercise being read is held back until
        // the next record begins or the file runs out.
        var pending: UserLesson? = null
        var setupLines = mutableListOf<String>()

        fun close() {
            val lesson = pending ?: return
            lessons += lesson.copy(
                setup = StoredExerciseSetup.decode(setupLines.joinToString(LINE_SEPARATOR)),
            )
            pending = null
            setupLines = mutableListOf()
        }

        stored.split(LINE_SEPARATOR).forEach { line ->
            val fields = line.split(FIELD_SEPARATOR)
            when {
                fields.size >= 3 && fields[0] == GROUP && fields[1].isNotEmpty() -> {
                    close()
                    groups += UserLessonGroup(
                        id = fields[1],
                        name = fields.drop(2).joinToString(FIELD_SEPARATOR),
                    )
                }

                fields.size >= 5 && fields[0] == LESSON && fields[1].isNotEmpty() -> {
                    close()
                    pending = UserLesson(
                        id = fields[1],
                        name = fields.drop(4).joinToString(FIELD_SEPARATOR),
                        groupId = fields[2],
                        trainerId = fields[3],
                        setup = ExerciseSetup.EMPTY,
                    )
                }

                fields.size >= 2 && fields[0] == SETUP && pending != null ->
                    setupLines += fields.drop(1).joinToString(FIELD_SEPARATOR)

                else -> Unit
            }
        }
        close()

        // A saved exercise whose group has gone would be one the player can neither reach nor
        // delete, so it goes to the first group instead of quietly disappearing.
        val known = groups.map { it.id }.toSet()
        val home = groups.firstOrNull()?.id
        val placed = when (home) {
            null -> emptyList()
            else -> lessons.map { if (it.groupId in known) it else it.copy(groupId = home) }
        }

        return UserLessonBook(
            groups = groups.ifEmpty { listOf(UserLessonBook.DEFAULT_GROUP) },
            lessons = placed,
        )
    }

    /**
     * A name as it can be written down: the player's own text, minus the two characters the format
     * itself uses.
     *
     * Cleaned rather than refused, because the alternative is losing an exercise over a character
     * in its title.
     */
    private fun String.storable(): String =
        replace(LINE_SEPARATOR, " ").replace(FIELD_SEPARATOR, " ").trim()
}

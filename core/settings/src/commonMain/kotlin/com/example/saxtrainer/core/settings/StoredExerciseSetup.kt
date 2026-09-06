package com.example.saxtrainer.core.settings

/**
 * What an exercise setup is stored under and how it is written down.
 *
 * The counterpart of [StoredSettings] for [ExerciseSetup], and shared for the same reason: storage
 * is per platform, the format must not be. One stored string per exercise keeps both platforms to a
 * single get and a single put, which is why this is a text encoding rather than a key per value -
 * `localStorage` has no notion of a group of keys, and enumerating one would be the only other way
 * to read a setup back whole.
 *
 * The format is one `name=value` per line. It is total in both directions: an entry whose name or
 * value could not survive the round trip is not written, and a line that does not parse is skipped,
 * so a setup written by another version reads as the entries this version does understand.
 */
internal object StoredExerciseSetup {

    private const val ENTRY_SEPARATOR = "\n"
    private const val VALUE_SEPARATOR = "="

    /** One key per exercise, kept clear of [StoredSettings]' own names. */
    fun keyOf(exercise: Exercise): String = "exercise_setup_${exercise.name}"

    fun encode(setup: ExerciseSetup): String = setup.asMap().entries
        .filter { (key, value) -> key.isStorableName() && !value.contains(ENTRY_SEPARATOR) }
        .joinToString(ENTRY_SEPARATOR) { (key, value) -> "$key$VALUE_SEPARATOR$value" }

    fun decode(stored: String?): ExerciseSetup {
        if (stored.isNullOrEmpty()) return ExerciseSetup.EMPTY
        val values = stored.split(ENTRY_SEPARATOR).mapNotNull { line ->
            val separator = line.indexOf(VALUE_SEPARATOR)
            if (separator <= 0) {
                null
            } else {
                line.substring(0, separator) to line.substring(separator + VALUE_SEPARATOR.length)
            }
        }
        return if (values.isEmpty()) ExerciseSetup.EMPTY else ExerciseSetup(values.toMap())
    }

    // A line is split at its first VALUE_SEPARATOR, so only the name is barred from holding one; a
    // value may. Neither may hold the separator that ends the line.
    private fun String.isStorableName(): Boolean =
        isNotEmpty() && !contains(ENTRY_SEPARATOR) && !contains(VALUE_SEPARATOR)
}

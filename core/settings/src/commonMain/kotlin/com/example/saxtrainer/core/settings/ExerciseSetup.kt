package com.example.saxtrainer.core.settings

/**
 * An exercise that can be set up before it is started, and whose setup is therefore worth
 * remembering.
 *
 * One entry per exercise the player configures. It is deliberately not [TrainingTool]: that names
 * the tools that show notes, so that they can be told how to show them, which is a different
 * question with a different set of answers.
 */
enum class Exercise {
    NOTE_TRAINER,
    LONG_TONES,
    NOTE_QUIZ,
    SEQUENCE,
    METRONOME,
    PHRASE_TRAINER,
    PHRASE_QUIZ,
}

/**
 * The setup one exercise was last started with, as plain names and values.
 *
 * Untyped on purpose. This module knows nothing about note sets, patterns or quiz modes - those
 * live in the features, and this module must not depend on them - so what travels is the smallest
 * thing both sides can agree on: strings. Each feature turns its own setup into these entries and
 * reads them back, and nobody else needs to understand them.
 *
 * Every reader is total. A value written by a newer version, a name this one no longer has, a
 * number that is not a number: all of them read as the default rather than throwing, because this
 * is on the way to the first screen of an exercise.
 */
class ExerciseSetup(private val values: Map<String, String> = emptyMap()) {

    val isEmpty: Boolean get() = values.isEmpty()

    fun string(key: String): String? = values[key]

    fun int(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default

    fun double(key: String, default: Double): Double = values[key]?.toDoubleOrNull() ?: default

    fun boolean(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    /** The entry of [entries] whose name was stored, or [default] for anything unrecognised. */
    fun <T : Enum<T>> enum(key: String, entries: List<T>, default: T): T =
        entries.firstOrNull { it.name == values[key] } ?: default

    /**
     * A stored list of whole numbers, or null when nothing was stored.
     *
     * Null rather than an empty list, because an empty list is a setup in its own right - no notes
     * chosen - and has to be told apart from never having chosen any.
     */
    fun ints(key: String): List<Int>? = values[key]
        ?.split(LIST_SEPARATOR)
        ?.filter { it.isNotBlank() }
        ?.mapNotNull(String::toIntOrNull)

    /** A stored list of words, or null when nothing was stored - null and empty differ as for [ints]. */
    fun strings(key: String): List<String>? = values[key]
        ?.split(LIST_SEPARATOR)
        ?.map(String::trim)
        ?.filter { it.isNotEmpty() }

    internal fun asMap(): Map<String, String> = values

    /**
     * Two setups are the same when they say the same things, whoever built them.
     *
     * Worth having rather than identity: the point of a setup is that it can be written down, read
     * back and compared with what was written, and that comparison is the only way to hold the two
     * directions of a codec to each other.
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is ExerciseSetup && values == other.values)

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String =
        values.entries.sortedBy { it.key }.joinToString(prefix = "ExerciseSetup(", postfix = ")")

    companion object {
        val EMPTY = ExerciseSetup()

        internal const val LIST_SEPARATOR = ","
    }
}

/** Collects the values an exercise wants remembered. See [exerciseSetup]. */
class ExerciseSetupBuilder internal constructor() {
    private val values = mutableMapOf<String, String>()

    fun put(key: String, value: String) {
        values[key] = value
    }

    fun put(key: String, value: Int) = put(key, value.toString())

    fun put(key: String, value: Double) = put(key, value.toString())

    fun put(key: String, value: Boolean) = put(key, value.toString())

    fun put(key: String, value: Enum<*>) = put(key, value.name)

    fun putInts(key: String, values: Iterable<Int>) =
        put(key, values.joinToString(ExerciseSetup.LIST_SEPARATOR))

    /** Words containing no comma; one that does is a bug in the caller, not something to store. */
    fun putStrings(key: String, values: Iterable<String>) {
        val words = values.toList()
        require(words.none { ExerciseSetup.LIST_SEPARATOR in it }) { "a stored word cannot hold a comma" }
        put(key, words.joinToString(ExerciseSetup.LIST_SEPARATOR))
    }

    internal fun build() = ExerciseSetup(values.toMap())
}

/** Builds the setup an exercise wants remembered, in the order its own screen asks for it. */
fun exerciseSetup(build: ExerciseSetupBuilder.() -> Unit): ExerciseSetup =
    ExerciseSetupBuilder().apply(build).build()

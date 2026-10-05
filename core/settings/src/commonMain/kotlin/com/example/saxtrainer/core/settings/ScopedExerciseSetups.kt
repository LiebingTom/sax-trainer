package com.example.saxtrainer.core.settings

/**
 * Setups kept apart by a scope of the caller's choosing - one per lesson step being edited - for as
 * long as the object lasts.
 *
 * Nothing here is written to storage: these are tries, not preferences. [save] and [restore] turn
 * the whole store into a list of strings and back, so the platform can carry it across the app
 * being closed in the background and reopened where it was.
 */
class ScopedExerciseSetups {
    private val scoped = mutableMapOf<String, ExerciseSetup>()
    private val latest = mutableMapOf<Exercise, ExerciseSetup>()

    /** What [scope] was last set up as, or [ExerciseSetup.EMPTY] when it never was. */
    fun setupOf(scope: String): ExerciseSetup = scoped[scope] ?: ExerciseSetup.EMPTY

    /**
     * A store an exercise can be handed: what it remembers lands under [scope], and reading it back
     * gives that scope's setup, whichever exercise asks.
     */
    fun repositoryFor(scope: String): ExerciseSetupRepository = object : ExerciseSetupRepository {
        override fun setupOf(exercise: Exercise): ExerciseSetup = this@ScopedExerciseSetups.setupOf(scope)

        override fun remember(exercise: Exercise, setup: ExerciseSetup) {
            scoped[scope] = setup
            latest[exercise] = setup
        }
    }

    /** The setup each exercise was last given in any scope - what was just being edited. */
    val latestByExercise: ExerciseSetupRepository = object : ExerciseSetupRepository {
        override fun setupOf(exercise: Exercise): ExerciseSetup = latest[exercise] ?: ExerciseSetup.EMPTY

        override fun remember(exercise: Exercise, setup: ExerciseSetup) {
            latest[exercise] = setup
        }
    }

    fun clear() {
        scoped.clear()
        latest.clear()
    }

    /** Every setup as pairs of strings: a scope or an exercise's marked name, then its setup. */
    fun save(): ArrayList<String> = ArrayList<String>().apply {
        scoped.forEach { (scope, setup) -> add(scope); add(StoredExerciseSetup.encode(setup)) }
        latest.forEach { (exercise, setup) -> add(EXERCISE_MARK + exercise.name); add(StoredExerciseSetup.encode(setup)) }
    }

    companion object {
        /** Starts a lesson scope's name never does, so the two kinds of entry cannot be confused. */
        private const val EXERCISE_MARK = "\u0001"

        /** A store holding what [save] wrote; anything it cannot read is left out. */
        fun restore(saved: List<String>): ScopedExerciseSetups = ScopedExerciseSetups().apply {
            saved.chunked(2).filter { it.size == 2 }.forEach { (name, encoded) ->
                val setup = StoredExerciseSetup.decode(encoded)
                if (name.startsWith(EXERCISE_MARK)) {
                    Exercise.entries.firstOrNull { it.name == name.removePrefix(EXERCISE_MARK) }
                        ?.let { latest[it] = setup }
                } else {
                    scoped[name] = setup
                }
            }
        }
    }
}

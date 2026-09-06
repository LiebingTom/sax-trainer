package com.example.saxtrainer.core.settings

/**
 * Where each exercise's last-started setup is kept.
 *
 * Separate from [SettingsRepository] because it answers a different question. [SettingsRepository]
 * holds what the player decided once and every tool obeys - the instrument, what notes are called;
 * this holds what one exercise happened to be set to the last time it was run, which is a default
 * offered rather than a rule followed.
 *
 * Remembered at Start, not at every switch flicked: a setup the player fiddled with and then left
 * without starting was never their answer to anything.
 */
interface ExerciseSetupRepository {
    /** [ExerciseSetup.EMPTY] for an exercise that has never been started. */
    fun setupOf(exercise: Exercise): ExerciseSetup

    fun remember(exercise: Exercise, setup: ExerciseSetup)
}

/**
 * Setups that last as long as the object does.
 *
 * The default an exercise falls back to when nobody handed it a store, and what tests use. An
 * exercise runs perfectly well without a memory of its last setup; it simply opens on its own
 * defaults every time.
 */
class InMemoryExerciseSetupRepository : ExerciseSetupRepository {
    private val setups = mutableMapOf<Exercise, ExerciseSetup>()

    override fun setupOf(exercise: Exercise): ExerciseSetup =
        setups[exercise] ?: ExerciseSetup.EMPTY

    override fun remember(exercise: Exercise, setup: ExerciseSetup) {
        setups[exercise] = setup
    }
}

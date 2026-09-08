package com.example.saxtrainer.core.settings

/**
 * Where each exercise's last setup is kept.
 *
 * Separate from [SettingsRepository] because it answers a different question. [SettingsRepository]
 * holds what the player decided once and every tool obeys - the instrument, what notes are called;
 * this holds what one exercise happened to be set to the last time it was used, which is a default
 * offered rather than a rule followed.
 *
 * When a setup is written down is the exercise's own decision, and there are two honest answers.
 * An exercise whose setup screen can be left without starting - by going back, which throws its
 * ViewModel away - has to write as the player chooses, or the choice is simply lost. One that is
 * configured and started in the same breath can wait until Start. Either way what is kept is a
 * default offered next time, never a rule.
 */
interface ExerciseSetupRepository {
    /** [ExerciseSetup.EMPTY] for an exercise that has never been set up. */
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

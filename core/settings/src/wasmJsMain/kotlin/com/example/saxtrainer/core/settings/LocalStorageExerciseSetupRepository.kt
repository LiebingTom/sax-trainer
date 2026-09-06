package com.example.saxtrainer.core.settings

import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import org.w3c.dom.get

/**
 * [ExerciseSetupRepository] backed by the browser's `localStorage`.
 *
 * The web counterpart of [SharedPreferencesExerciseSetupRepository][ExerciseSetupRepository]: same
 * names, same format, same store as the app's settings. `localStorage` is already scoped to this
 * origin, so there is no file to name.
 *
 * [storage] is a parameter only so a test can hand in a store of its own; the app uses the default.
 */
class LocalStorageExerciseSetupRepository(
    private val storage: Storage = localStorage,
) : ExerciseSetupRepository {

    override fun setupOf(exercise: Exercise): ExerciseSetup =
        StoredExerciseSetup.decode(storage[StoredExerciseSetup.keyOf(exercise)])

    override fun remember(exercise: Exercise, setup: ExerciseSetup) {
        storage.setItem(StoredExerciseSetup.keyOf(exercise), StoredExerciseSetup.encode(setup))
    }
}

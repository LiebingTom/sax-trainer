package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.core.content.edit

/**
 * [ExerciseSetupRepository] backed by [android.content.SharedPreferences].
 *
 * The same file the app's settings live in, because it is the same kind of value kept for the same
 * length of time; what is written under which name is [StoredExerciseSetup] and shared with every
 * other platform.
 */
class SharedPreferencesExerciseSetupRepository(
    context: Context,
) : ExerciseSetupRepository {

    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(StoredSettings.NAME, Context.MODE_PRIVATE)
    }

    override fun setupOf(exercise: Exercise): ExerciseSetup =
        StoredExerciseSetup.decode(preferences.getString(StoredExerciseSetup.keyOf(exercise), null))

    override fun remember(exercise: Exercise, setup: ExerciseSetup) {
        preferences.edit {
            putString(StoredExerciseSetup.keyOf(exercise), StoredExerciseSetup.encode(setup))
        }
    }
}

package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [UserLessonRepository] backed by [android.content.SharedPreferences].
 *
 * The same file the app's settings and exercise setups live in, because it is the same kind of
 * value kept for the same length of time; what is written under which name is [StoredUserLessons]
 * and shared with every other platform.
 *
 * The book is held in memory as well as on disk: it is drawn on screen, so it has to be something
 * the UI can collect, and reading it back off disk for every frame would be the only alternative.
 */
class SharedPreferencesUserLessonRepository(
    context: Context,
) : UserLessonRepository {

    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(StoredSettings.NAME, Context.MODE_PRIVATE)
    }

    private val _book = MutableStateFlow(
        StoredUserLessons.decode(preferences.getString(StoredUserLessons.KEY, null)),
    )
    override val book: StateFlow<UserLessonBook> = _book.asStateFlow()

    override fun write(book: UserLessonBook) {
        _book.value = book
        preferences.edit { putString(StoredUserLessons.KEY, StoredUserLessons.encode(book)) }
    }
}

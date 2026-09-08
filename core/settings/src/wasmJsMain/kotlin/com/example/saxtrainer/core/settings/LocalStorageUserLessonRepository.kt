package com.example.saxtrainer.core.settings

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.Storage
import org.w3c.dom.get

/**
 * [UserLessonRepository] backed by the browser's `localStorage`.
 *
 * The web counterpart of [SharedPreferencesUserLessonRepository][UserLessonRepository]: same name,
 * same format, same store as the app's settings. `localStorage` is already scoped to this origin,
 * so there is no file to name.
 *
 * [storage] is a parameter only so a test can hand in a store of its own; the app uses the default.
 */
class LocalStorageUserLessonRepository(
    private val storage: Storage = localStorage,
) : UserLessonRepository {

    private val _book = MutableStateFlow(StoredUserLessons.decode(storage[StoredUserLessons.KEY]))
    override val book: StateFlow<UserLessonBook> = _book.asStateFlow()

    override fun write(book: UserLessonBook) {
        _book.value = book
        storage.setItem(StoredUserLessons.KEY, StoredUserLessons.encode(book))
    }
}

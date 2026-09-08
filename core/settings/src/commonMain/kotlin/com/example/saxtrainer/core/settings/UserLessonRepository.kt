package com.example.saxtrainer.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the practices the player saved are kept.
 *
 * Two members, like [ExerciseSetupRepository]: a book is read whole and written whole. Every rule
 * about adding, renaming, reordering and deleting is a function on [UserLessonBook] instead of a
 * method here, so there is nothing for a store to get subtly different from what the screen does -
 * and nothing to keep in step across two platforms.
 *
 * A [StateFlow] rather than a plain read, because unlike a last-used setup this is drawn on screen:
 * saving an exercise has to show up in the list without anybody going looking for it.
 */
interface UserLessonRepository {
    val book: StateFlow<UserLessonBook>

    fun write(book: UserLessonBook)
}

/**
 * Saved practices that last as long as the object does.
 *
 * What tests use, and what an entry point without a store would fall back to: the app still works,
 * it simply forgets what was saved when it closes.
 */
class InMemoryUserLessonRepository(
    initial: UserLessonBook = UserLessonBook(),
) : UserLessonRepository {

    private val _book = MutableStateFlow(initial)
    override val book: StateFlow<UserLessonBook> = _book.asStateFlow()

    override fun write(book: UserLessonBook) {
        _book.value = book
    }
}

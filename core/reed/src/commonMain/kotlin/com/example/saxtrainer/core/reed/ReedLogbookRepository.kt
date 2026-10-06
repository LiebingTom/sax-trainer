package com.example.saxtrainer.core.reed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the reed logbook is kept.
 *
 * Two members, like the lesson book's store: the logbook is read whole and written whole, and every
 * rule is a function on [ReedLogbook] instead of a method here, so there is nothing for a platform's
 * store to get subtly different.
 *
 * A [StateFlow] because three places show it at once - the logbook, the tile on the landing page,
 * the notification - and each has to see a session start or stop the moment it happens.
 */
interface ReedLogbookRepository {
    val logbook: StateFlow<ReedLogbook>

    fun write(logbook: ReedLogbook)
}

/** A logbook that lasts as long as the object: what tests use, and the fallback without a store. */
class InMemoryReedLogbookRepository(
    initial: ReedLogbook = ReedLogbook(),
) : ReedLogbookRepository {

    private val _logbook = MutableStateFlow(initial)
    override val logbook: StateFlow<ReedLogbook> = _logbook.asStateFlow()

    override fun write(logbook: ReedLogbook) {
        _logbook.value = logbook
    }
}

/**
 * A logbook kept as one string under one name, wherever that string lives.
 *
 * What both platforms' stores are, minus the platform: [read] and [save] are the only two calls a
 * platform has to supply.
 */
open class StringBackedReedLogbookRepository(
    read: () -> String?,
    private val save: (String) -> Unit,
) : ReedLogbookRepository {

    private val _logbook = MutableStateFlow(StoredReedLogbook.decode(read()))
    override val logbook: StateFlow<ReedLogbook> = _logbook.asStateFlow()

    override fun write(logbook: ReedLogbook) {
        _logbook.value = logbook
        save(StoredReedLogbook.encode(logbook))
    }

    companion object {
        /** The name the logbook is stored under, on every platform. */
        const val KEY: String = StoredReedLogbook.KEY
    }
}

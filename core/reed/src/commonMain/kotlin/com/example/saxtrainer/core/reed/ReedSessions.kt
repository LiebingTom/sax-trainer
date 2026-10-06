package com.example.saxtrainer.core.reed

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Starting and stopping a session, the one way every place does it.
 *
 * The logbook, the tile on the landing page and the notification all start and stop through this,
 * and all read the same [ReedLogbookRepository.logbook], so they cannot disagree about what is
 * running. Each call reads the logbook as it is, applies the rule, and writes the result; a call
 * the rules do not allow changes nothing.
 *
 * [now] and [newId] are parameters so a test decides what time it is and what an id looks like.
 */
class ReedSessions(
    private val repository: ReedLogbookRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    /** Starts a session on whichever reed is selected. */
    fun start() {
        val reed = repository.logbook.value.effectiveSelection() ?: return
        start(reed.id)
    }

    /** Starts a session on [reedId]. */
    fun start(reedId: String) {
        update { it.started(newId(), reedId, now()) }
    }

    fun stop() {
        update { it.stopped(now()) }
    }

    private inline fun update(change: (ReedLogbook) -> ReedLogbook) {
        val before = repository.logbook.value
        val after = change(before)
        if (after != before) repository.write(after)
    }
}

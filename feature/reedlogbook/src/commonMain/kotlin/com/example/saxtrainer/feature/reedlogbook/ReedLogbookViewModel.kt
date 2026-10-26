package com.example.saxtrainer.feature.reedlogbook

import androidx.lifecycle.ViewModel
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedLogbookCsv
import com.example.saxtrainer.core.reed.ReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedSessions
import com.example.saxtrainer.core.reed.ReedStatus
import com.example.saxtrainer.core.reed.minusReed
import com.example.saxtrainer.core.reed.minusSession
import com.example.saxtrainer.core.reed.selected
import com.example.saxtrainer.core.reed.withStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * The logbook as the screens use it: what is in it, and everything that can be done to it apart
 * from describing a reed (see [ReedEditViewModel]).
 *
 * Nothing is held here that the logbook does not already hold - the state is the store's own - so
 * this screen, the tile on the landing page and the notification always show the same thing.
 * Sessions start and stop through [sessions], the one way every place does it.
 */
class ReedLogbookViewModel(
    private val repository: ReedLogbookRepository,
    private val sessions: ReedSessions,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    val logbook: StateFlow<ReedLogbook> = repository.logbook

    fun select(reedId: String) = update { it.selected(reedId) }

    fun start() = sessions.start()

    fun stop() = sessions.stop()

    /** Enters a session played earlier, by hand. */
    fun addSession(reedId: String, start: Instant, end: Instant) = sessions.add(reedId, start, end)

    /** Puts a reed aside or takes it back; [number] is what it returns under, when it was asked. */
    fun setStatus(reedId: String, status: ReedStatus, number: Int? = null) =
        update { it.withStatus(reedId, status, now(), number) }

    /** Deletes a reed and every session on it. */
    fun deleteReed(reedId: String) = update { it.minusReed(reedId) }

    fun deleteSession(sessionId: String) = update { it.minusSession(sessionId) }

    /** The whole logbook as the CSV file the export saves. */
    fun csv(): String = ReedLogbookCsv.of(repository.logbook.value)

    private inline fun update(change: (ReedLogbook) -> ReedLogbook) {
        val before = repository.logbook.value
        val after = change(before)
        if (after != before) repository.write(after)
    }
}

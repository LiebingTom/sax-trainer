package com.example.saxtrainer.core.reed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Starting and stopping, as the logbook, the landing page and the notification all do it. */
class ReedSessionsTest {

    private val t0 = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private var now = t0
    private var ids = 0

    private fun sessionsOver(repository: ReedLogbookRepository) =
        ReedSessions(repository, now = { now }, newId = { "s${ids++}" })

    private val twoReeds = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .plusReed("b", 2, "daddario", "daddario-royal", "3", t0)

    @Test
    fun `start plays the selected reed`() {
        val repository = InMemoryReedLogbookRepository(twoReeds.selected("a"))

        sessionsOver(repository).start()

        assertEquals("a", repository.logbook.value.runningSession?.reedId)
    }

    @Test
    fun `start with no reed in rotation does nothing`() {
        val repository = InMemoryReedLogbookRepository()

        sessionsOver(repository).start()

        assertNull(repository.logbook.value.runningSession)
    }

    @Test
    fun `stop with nothing running changes nothing`() {
        val repository = InMemoryReedLogbookRepository(twoReeds)

        sessionsOver(repository).stop()

        assertEquals(twoReeds, repository.logbook.value)
    }

    @Test
    fun `a session goes on being timed from its start after the app was closed`() {
        var stored: String? = null
        fun reopen() = StringBackedReedLogbookRepository(read = { stored }, save = { stored = it })

        val first = reopen()
        first.write(twoReeds)
        sessionsOver(first).start("b")

        // Closed and opened again an hour later: everything there is to know is what was stored.
        now = t0 + 60.minutes
        val reopened = reopen()
        val running = reopened.logbook.value.runningSession

        assertEquals("b", running?.reedId)
        assertEquals(60.minutes, running?.durationAt(now))

        sessionsOver(reopened).stop()
        assertEquals(60.minutes, reopen().logbook.value.sessions.single().durationAt(t0 + 999.minutes))
    }
}

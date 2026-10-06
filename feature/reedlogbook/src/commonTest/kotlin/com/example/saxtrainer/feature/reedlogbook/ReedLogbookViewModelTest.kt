package com.example.saxtrainer.feature.reedlogbook

import com.example.saxtrainer.core.reed.InMemoryReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedSessions
import com.example.saxtrainer.core.reed.ReedStatus
import com.example.saxtrainer.core.reed.plusReed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The logbook screen's actions, on a clock and ids the test decides. */
class ReedLogbookViewModelTest {

    private val t0 = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private var now = t0
    private var ids = 0

    private fun viewModel(logbook: ReedLogbook = ReedLogbook()): Pair<ReedLogbookViewModel, InMemoryReedLogbookRepository> {
        val repository = InMemoryReedLogbookRepository(logbook)
        val sessions = ReedSessions(repository, now = { now }, newId = { "s${ids++}" })
        return ReedLogbookViewModel(repository, sessions, now = { now }) to repository
    }

    private val twoReeds = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .plusReed("b", 2, "daddario", "daddario-royal", "3", t0)

    @Test
    fun `with no reed there is nothing to start`() {
        val (viewModel, _) = viewModel()

        assertFalse(viewModel.logbook.value.canStart)
        viewModel.start()
        assertNull(viewModel.logbook.value.runningSession)
    }

    @Test
    fun `a session is timed from start to stop on the selected reed`() {
        val (viewModel, _) = viewModel(twoReeds)
        viewModel.select("a")

        viewModel.start()
        now = t0 + 25.minutes
        viewModel.stop()

        val session = viewModel.logbook.value.sessionsOf("a").single()
        assertEquals(25.minutes, session.durationAt(t0 + 99.minutes))
        assertTrue(viewModel.logbook.value.canStart)
    }

    @Test
    fun `the reed chosen last is still selected when the logbook is opened again`() {
        val (first, repository) = viewModel(twoReeds)
        first.select("a")

        val reopened = ReedLogbookViewModel(repository, ReedSessions(repository))

        assertEquals("a", reopened.logbook.value.effectiveSelection()?.id)
    }

    @Test
    fun `marking a reed broken frees its number and keeps its sessions`() {
        val (viewModel, _) = viewModel(twoReeds)
        viewModel.select("a")
        viewModel.start()
        now = t0 + 10.minutes
        viewModel.setStatus("a", ReedStatus.INACTIVE)

        val logbook = viewModel.logbook.value
        assertEquals(listOf(1), logbook.freeNumbers())
        assertEquals(1, logbook.sessionsOf("a").size)
        assertNull(logbook.runningSession, "a broken reed is no longer played")
    }

    @Test
    fun `a deleted session is gone from its reed`() {
        val (viewModel, _) = viewModel(twoReeds)
        viewModel.start()
        viewModel.stop()
        val id = viewModel.logbook.value.sessions.single().id

        viewModel.deleteSession(id)

        assertTrue(viewModel.logbook.value.sessions.isEmpty())
    }

    @Test
    fun `the export holds every reed`() {
        val (viewModel, _) = viewModel(twoReeds)

        val rows = viewModel.csv().trimEnd().lines()

        assertEquals(3, rows.size, "the header and one row for each reed never played")
    }
}

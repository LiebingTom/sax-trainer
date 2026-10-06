package com.example.saxtrainer.feature.reedlogbook

import com.example.saxtrainer.core.reed.InMemoryReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedStatus
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.reed.withStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Adding and describing a reed, through the logbook's own rules. */
class ReedEditViewModelTest {

    private val t0 = Instant.fromEpochMilliseconds(1_790_000_000_000)

    private val threeReeds = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .plusReed("b", 2, "daddario", "daddario-royal", "2.5", t0 + 1.minutes)
        .plusReed("c", 3, "vandoren", "vandoren-java", "3", t0 + 2.minutes)

    private fun adding(logbook: ReedLogbook, repository: InMemoryReedLogbookRepository = InMemoryReedLogbookRepository(logbook)) =
        ReedEditViewModel(repository, reedId = null, now = { t0 }, newId = { "new" }) to repository

    @Test
    fun `a new reed starts as the last one added, under the lowest free number`() {
        val broken = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0)
        val (viewModel, _) = adding(broken)

        val state = viewModel.uiState.value

        assertTrue(state.isNew)
        assertEquals(ReedDraft("vandoren", "vandoren-java", "3", 1), state.draft)
        assertEquals(listOf(1), state.numbers)
    }

    @Test
    fun `a new reed is saved, numbered and selected`() {
        val (viewModel, repository) = adding(threeReeds)

        val id = viewModel.save()

        assertEquals("new", id)
        val reed = repository.logbook.value.reedOf("new")
        assertEquals(4, reed?.number)
        assertEquals("new", repository.logbook.value.selectedReedId)
    }

    @Test
    fun `another maker brings its first product and keeps a strength it also has`() {
        val (viewModel, _) = adding(ReedLogbook())

        viewModel.selectStrength("3")
        viewModel.selectManufacturer("vandoren")

        val draft = viewModel.uiState.value.draft
        assertEquals("vandoren-traditional", draft.productId)
        assertEquals("3", draft.strength)
    }

    @Test
    fun `a product on another scale falls back to a strength it has`() {
        val (viewModel, _) = adding(ReedLogbook())

        viewModel.selectProduct("daddario-la-voz")

        assertEquals("M", viewModel.uiState.value.draft.strength)
    }

    @Test
    fun `a number taken in the meantime is refused and nothing is written`() {
        val repository = InMemoryReedLogbookRepository(threeReeds)
        val (viewModel, _) = adding(threeReeds, repository)
        // Someone else takes 4 while the form is open.
        repository.write(threeReeds.plusReed("d", 4, "daddario", "daddario-royal", "2.5", t0))
        val before = repository.logbook.value

        assertNull(viewModel.save())
        assertEquals(before, repository.logbook.value)
    }

    @Test
    fun `editing renumbers a reed to a free number and describes it again`() {
        val broken = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0)
        val repository = InMemoryReedLogbookRepository(broken)
        val viewModel = ReedEditViewModel(repository, reedId = "c")
        assertEquals(listOf(1, 3), viewModel.uiState.value.numbers, "a free number, or its own")

        viewModel.selectNumber(1)
        viewModel.selectStrength("3.5")

        assertEquals("c", viewModel.save())
        val reed = repository.logbook.value.reedOf("c")
        assertEquals(1, reed?.number)
        assertEquals("3.5", reed?.strength)
    }

    @Test
    fun `a reed out of rotation keeps the number it had`() {
        val broken = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0)
        val viewModel = ReedEditViewModel(InMemoryReedLogbookRepository(broken), reedId = "a")

        assertTrue(viewModel.uiState.value.numbers.isEmpty())
        assertTrue(viewModel.uiState.value.canSave)
    }
}

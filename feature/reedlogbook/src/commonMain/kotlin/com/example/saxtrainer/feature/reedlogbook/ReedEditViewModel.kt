package com.example.saxtrainer.feature.reedlogbook

import androidx.lifecycle.ViewModel
import com.example.saxtrainer.core.reed.ReedCatalog
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedManufacturer
import com.example.saxtrainer.core.reed.ReedProduct
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.reed.renumbered
import com.example.saxtrainer.core.reed.withDetails
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a reed is being described as, before it is saved. */
data class ReedDraft(
    val manufacturerId: String,
    val productId: String,
    val strength: String,
    val number: Int,
    /** The price as typed, in euros - "4.50", "4,50" or nothing at all. */
    val price: String = "",
)

/**
 * The add/edit form: a reed's make, model, strength and number.
 *
 * - [isNew]: adding rather than editing.
 * - [numbers]: what it may be numbered, its own number included; empty for a reed out of rotation,
 *   which keeps the number it had.
 */
data class ReedEditUiState(
    val isNew: Boolean,
    val draft: ReedDraft,
    val numbers: List<Int>,
) {
    val manufacturers: List<ReedManufacturer> get() = ReedCatalog.manufacturers

    val products: List<ReedProduct> get() = ReedCatalog.manufacturerOf(draft.manufacturerId)?.products.orEmpty()

    /** The product's own scale; a stored strength it does not list is offered too, so it is kept. */
    val strengths: List<String>
        get() {
            val scale = ReedCatalog.productOf(draft.productId)?.strengths.orEmpty()
            return if (draft.strength in scale) scale else scale + draft.strength
        }

    /** The typed price in cents; null when nothing is typed or it is not a price. */
    val priceCents: Long? get() = ReedText.parsePrice(draft.price)

    /** Nothing typed is fine - the price is optional - but something typed has to be a price. */
    val isPriceValid: Boolean get() = draft.price.isBlank() || priceCents != null

    val canSave: Boolean
        get() = draft.strength.isNotBlank() && isPriceValid && (numbers.isEmpty() || draft.number in numbers)
}

/**
 * Adds a reed ([reedId] null) or edits one.
 *
 * Saving goes through the logbook's own rules, so a number another reed in rotation has cannot be
 * saved even if the form offered it. [now] and [newId] are parameters so a test decides both.
 */
class ReedEditViewModel(
    private val repository: ReedLogbookRepository,
    private val reedId: String?,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState(repository.logbook.value))
    val uiState: StateFlow<ReedEditUiState> = _uiState.asStateFlow()

    private val draft: ReedDraft get() = _uiState.value.draft

    /** Another maker: its first product, keeping the strength where that product has it too. */
    fun selectManufacturer(id: String) {
        val maker = ReedCatalog.manufacturerOf(id) ?: return
        if (maker.id == draft.manufacturerId) return
        choose(maker.products.first(), manufacturerId = maker.id)
    }

    fun selectProduct(id: String) {
        val product = ReedCatalog.productOf(id) ?: return
        choose(product, manufacturerId = draft.manufacturerId)
    }

    fun selectStrength(strength: String) = edit { it.copy(strength = strength) }

    fun selectNumber(number: Int) = edit { it.copy(number = number) }

    fun typePrice(text: String) = edit { it.copy(price = text) }

    /**
     * Writes the reed and returns its id, or null when the logbook refused it - a number taken in
     * the meantime, say. Nothing is written then.
     */
    fun save(): String? {
        val state = _uiState.value
        if (!state.canSave) return null
        val d = state.draft
        val before = repository.logbook.value

        val id: String
        val after: ReedLogbook
        if (reedId == null) {
            id = newId()
            after = before.plusReed(id, d.number, d.manufacturerId, d.productId, d.strength, now(), state.priceCents)
            if (after.reedOf(id) == null) return null
        } else {
            id = reedId
            val described = before.withDetails(id, d.manufacturerId, d.productId, d.strength, state.priceCents)
            val reed = described.reedOf(id) ?: return null
            after = if (reed.isActive && reed.number != d.number) described.renumbered(id, d.number) else described
            if (reed.isActive && after.reedOf(id)?.number != d.number) return null
        }
        if (after != before) repository.write(after)
        return id
    }

    private fun choose(product: ReedProduct, manufacturerId: String) = edit {
        val strength = when {
            it.strength in product.strengths -> it.strength
            ReedCatalog.DEFAULT_STRENGTH in product.strengths -> ReedCatalog.DEFAULT_STRENGTH
            else -> product.strengths[product.strengths.size / 2]
        }
        it.copy(manufacturerId = manufacturerId, productId = product.id, strength = strength)
    }

    private inline fun edit(change: (ReedDraft) -> ReedDraft) {
        _uiState.value = _uiState.value.copy(draft = change(draft))
    }

    private fun initialState(logbook: ReedLogbook): ReedEditUiState {
        val existing = reedId?.let(logbook::reedOf)
        if (existing == null) {
            // A new reed starts as the last one added: most players buy the same box again.
            val previous = logbook.reedsNewestFirst.firstOrNull()
            val draft = ReedDraft(
                manufacturerId = previous?.manufacturerId ?: ReedCatalog.defaultManufacturer.id,
                productId = previous?.productId ?: ReedCatalog.defaultProduct.id,
                strength = previous?.strength ?: ReedCatalog.DEFAULT_STRENGTH,
                number = logbook.nextFreeNumber(),
                price = previous?.priceCents?.let(ReedText::priceInputOf).orEmpty(),
            )
            return ReedEditUiState(isNew = true, draft = draft, numbers = logbook.freeNumbers())
        }
        val numbers = if (existing.isActive) {
            (1..logbook.activeReeds.size + 1).filter { logbook.isNumberFree(it, exceptReedId = existing.id) }
        } else {
            emptyList()
        }
        val draft = ReedDraft(
            manufacturerId = existing.manufacturerId,
            productId = existing.productId,
            strength = existing.strength,
            number = existing.number,
            price = existing.priceCents?.let(ReedText::priceInputOf).orEmpty(),
        )
        return ReedEditUiState(isNew = false, draft = draft, numbers = numbers)
    }
}

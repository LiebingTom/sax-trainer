package com.example.saxtrainer.core.phrase

/**
 * Where a [Phrase] is looked up by id.
 *
 * A plain interface rather than any one implementation, so a future Phrase Library screen - or a
 * platform-specific, file-backed catalog - can be handed to a trainer without it caring which kind
 * it got.
 */
interface PhraseCatalog {
    val all: List<Phrase>
    fun byId(id: String): Phrase?
}

/** The simplest [PhraseCatalog] there is: a fixed list, already loaded. */
data class InMemoryPhraseCatalog(override val all: List<Phrase>) : PhraseCatalog {
    override fun byId(id: String): Phrase? = all.firstOrNull { it.id == id }
}

package com.example.saxtrainer.core.phrase

/** Where a [PhraseRecipe] is looked up by id - the pattern-recipe counterpart of [PhraseCatalog]. */
interface PatternCatalog {
    val all: List<PhraseRecipe>
    fun byId(id: String): PhraseRecipe?
}

/** The simplest [PatternCatalog] there is: a fixed list, already loaded. */
data class InMemoryPatternCatalog(override val all: List<PhraseRecipe>) : PatternCatalog {
    override fun byId(id: String): PhraseRecipe? = all.firstOrNull { it.id == id }
}

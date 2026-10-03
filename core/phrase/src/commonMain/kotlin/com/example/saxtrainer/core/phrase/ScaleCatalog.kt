package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.Scale

/** Where a [Scale] is looked up by id - the scale counterpart of [PatternCatalog]. */
interface ScaleCatalog {
    val all: List<Scale>
    fun byId(id: String): Scale?
}

/** The simplest [ScaleCatalog] there is: a fixed list, already loaded. */
data class InMemoryScaleCatalog(override val all: List<Scale>) : ScaleCatalog {
    override fun byId(id: String): Scale? = all.firstOrNull { it.id == id }
}

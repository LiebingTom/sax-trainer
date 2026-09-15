package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scale

/** Which kind of [PhraseSource] a setup screen is currently offering. */
enum class PhraseSourceType { LIBRARY, SCALE_PATTERN }

/**
 * Where a phrase for a practice session comes from: a maintained [PhraseLibrary] entry, or a
 * [PhraseRecipe] from [PatternLibrary] resolved against a scale and a root.
 *
 * Trainer and Quiz setup both switch on this the same way, and once a source is [resolve]d into a
 * [Phrase] neither one knows or cares which kind it was - the practice screens are source-agnostic by
 * construction.
 */
sealed interface PhraseSource {

    /** Resolves this source into a [Phrase], or null when an id no longer exists. */
    fun resolve(phrases: PhraseCatalog, patterns: PatternCatalog): Phrase?

    /** A phrase looked up by id, persisted the same way [Phrase.id] survives a version of the app. */
    data class Library(val phraseId: String) : PhraseSource {
        override fun resolve(phrases: PhraseCatalog, patterns: PatternCatalog): Phrase? = phrases.byId(phraseId)
    }

    /**
     * A phrase generated from a stored [PhraseRecipe], played on [scale] from [root] in every one of
     * [octaves] - see [PhraseRecipe.resolve].
     *
     * [root] and [octaves] are the player's choice, never the pattern's: the recipe itself carries
     * neither.
     */
    data class ScalePattern(
        val patternId: String,
        val scale: Scale,
        val root: NoteName,
        val octaves: Set<Int>,
    ) : PhraseSource {
        init {
            require(octaves.isNotEmpty()) { "a scale pattern source needs at least one octave" }
        }

        override fun resolve(phrases: PhraseCatalog, patterns: PatternCatalog): Phrase? =
            patterns.byId(patternId)?.resolve(scale, root, octaves)
    }
}

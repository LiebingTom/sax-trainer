package com.example.saxtrainer.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Writing down which tools are starred, and reading them back.
 *
 * The set is stored as one line, so what has to hold is that the line survives a round trip and
 * that a line which does not make sense reads as nothing starred rather than as a failure - this
 * runs on the way to the first screen, like every other setting.
 */
class StoredSettingsFavoritesTest {

    @Test
    fun `a set survives being written down and read back`() {
        val favorites = setOf("NOTE_QUIZ", "METRONOME", "LONG_TONES")

        val restored = StoredSettings.favoritesOf(StoredSettings.encodeFavorites(favorites))

        assertEquals(favorites, restored)
    }

    @Test
    fun `nothing starred is written as nothing and reads as nothing`() {
        assertEquals("", StoredSettings.encodeFavorites(emptySet()))
        assertEquals(emptySet(), StoredSettings.favoritesOf(""))
    }

    @Test
    fun `nothing stored reads as nothing starred`() {
        assertEquals(emptySet(), StoredSettings.favoritesOf(null))
    }

    @Test
    fun `a line of nothing but separators reads as nothing starred`() {
        assertEquals(emptySet(), StoredSettings.favoritesOf(",,,"))
    }

    @Test
    fun `a blank name is never written down`() {
        // Nothing can be starred that has no name, and a blank one would read back as nothing
        // anyway - dropping it on the way in keeps the line free of empty entries.
        assertEquals("NOTE_QUIZ", StoredSettings.encodeFavorites(setOf("NOTE_QUIZ", "", "  ")))
    }

    @Test
    fun `a name carrying the separator is dropped rather than split in two`() {
        val encoded = StoredSettings.encodeFavorites(setOf("NOTE_QUIZ", "ONE,TWO"))

        assertEquals("NOTE_QUIZ", encoded)
        assertEquals(setOf("NOTE_QUIZ"), StoredSettings.favoritesOf(encoded))
    }

    @Test
    fun `a name this version knows nothing about is kept`() {
        // Storage keeps names, not tools: what a name means is the caller's, so a set written by a
        // newer version comes back through an older one intact.
        val restored = StoredSettings.favoritesOf(StoredSettings.encodeFavorites(setOf("TUNER")))

        assertTrue("TUNER" in restored)
    }
}

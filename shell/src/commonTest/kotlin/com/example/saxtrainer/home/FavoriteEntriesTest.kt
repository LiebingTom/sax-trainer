package com.example.saxtrainer.home

import com.example.saxtrainer.core.learning.TrainerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning stored names back into cards.
 *
 * The one piece of logic favorites add: everything else about them is a set going into storage and
 * coming back out again.
 */
class FavoriteEntriesTest {

    @Test
    fun `starred tools come back in the order the catalogue lists them`() {
        // Given in the opposite order to the catalogue, which lists the quiz first and the
        // metronome last.
        val entries = favoriteEntriesOf(setOf(TrainerId.METRONOME.name, TrainerId.NOTE_QUIZ.name))

        assertEquals(
            listOf(TrainerId.NOTE_QUIZ, TrainerId.METRONOME),
            entries.map { it.trainerId },
            "the catalogue's order, not the set's",
        )
    }

    @Test
    fun `every practice tool can be starred`() {
        val all = TrainerId.entries.map { it.name }.toSet()

        val entries = favoriteEntriesOf(all)

        // Compared as a set: which tools can be starred is this test's business, and the order they
        // come back in is the catalogue's rather than the enum's - the test above is where that is
        // pinned down.
        assertEquals(TrainerId.entries.toSet(), entries.map { it.trainerId }.toSet())
    }

    @Test
    fun `a name no tool has is passed over rather than failing`() {
        val entries = favoriteEntriesOf(setOf("EAR_TRAINING", TrainerId.NOTE_QUIZ.name))

        assertEquals(listOf(TrainerId.NOTE_QUIZ), entries.map { it.trainerId })
    }

    @Test
    fun `a star left on the retired fingering trainer is passed over`() {
        // Stored by a version that still had the tool; the name now resolves to nothing.
        val entries = favoriteEntriesOf(setOf("FINGERING", TrainerId.NOTE_QUIZ.name))

        assertEquals(listOf(TrainerId.NOTE_QUIZ), entries.map { it.trainerId })
    }

    @Test
    fun `nothing starred is no cards`() {
        assertEquals(emptyList(), favoriteEntriesOf(emptySet()))
    }

    @Test
    fun `only a practice tool carries a trainer, so only one can be starred`() {
        // The landing page's other entries - the two lists and the settings - name no trainer, and
        // so cannot be reached by a favorite whatever is stored.
        assertTrue(appEntries.all { it.trainerId == null })
    }

    @Test
    fun `a starred reed logbook is drawn as its session tile rather than a square`() {
        val starred = favoriteEntriesOf(setOf(TrainerId.REED_LOGBOOK.name, TrainerId.NOTE_QUIZ.name))

        assertEquals(listOf(TrainerId.NOTE_QUIZ), squareFavoritesOf(starred).map { it.trainerId })
        assertTrue(showsReedSessionTile(setOf(TrainerId.REED_LOGBOOK.name), sessionRunning = false))
    }

    @Test
    fun `a running reed session is shown whether the logbook is starred or not`() {
        assertTrue(showsReedSessionTile(emptySet(), sessionRunning = true))
        assertEquals(false, showsReedSessionTile(emptySet(), sessionRunning = false))
    }
}

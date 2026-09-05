package com.example.saxtrainer.core.settings

import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.browser.localStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The web half of the settings round trip, against a real browser's `localStorage`.
 *
 * The mirror image of the Android instrumented test, and deliberately the same cases: the two
 * platforms are only worth having if a setting written on one behaves the way it does on the other.
 * A reload is a fresh repository over the same store, so that is what [reloaded] builds.
 */
class LocalStorageSettingsRepositoryTest {

    private fun reloaded() = LocalStorageSettingsRepository(localStorage)

    @BeforeTest
    @AfterTest
    fun clearStoredSettings() {
        localStorage.clear()
    }

    @Test
    fun an_untouched_origin_reads_the_defaults() {
        assertEquals(AppSettings(), reloaded().settings.value)
    }

    @Test
    fun the_instrument_survives_a_reload() {
        reloaded().setInstrument(Instrument.BARITONE_SAX)

        assertEquals(Instrument.BARITONE_SAX, reloaded().settings.value.instrument)
    }

    @Test
    fun the_note_naming_survives_a_reload() {
        reloaded().setNoteNaming(NoteNaming.GERMAN)

        assertEquals(NoteNaming.GERMAN, reloaded().settings.value.noteNaming)
    }

    @Test
    fun the_shared_note_display_survives_a_reload() {
        reloaded().setNoteDisplay(NoteDisplay.STAFF)

        assertEquals(NoteDisplay.STAFF, reloaded().settings.value.noteDisplay)
    }

    @Test
    fun the_count_in_survives_a_reload() {
        val repository = reloaded()
        repository.setCountInBeats(8)
        repository.setCountInClick(false)

        val restored = reloaded().settings.value
        assertEquals(8, restored.countInBeats)
        assertEquals(false, restored.countInClick)
    }

    @Test
    fun no_count_in_survives_a_reload_rather_than_reading_as_the_default() {
        reloaded().setCountInBeats(0)

        assertEquals(0, reloaded().settings.value.countInBeats)
    }

    @Test
    fun each_tool_keeps_its_own_note_display_across_a_reload() {
        val repository = reloaded()
        repository.setNoteDisplay(NoteDisplay.NAME)
        repository.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.STAFF)

        val restored = reloaded().settings.value
        assertEquals(NoteDisplay.STAFF, restored.noteDisplayFor(TrainingTool.LONG_TONES))
        assertEquals(NoteDisplay.NAME, restored.noteDisplayFor(TrainingTool.FINGERING))
        assertEquals(NoteDisplay.NAME, restored.noteDisplay)
    }

    @Test
    fun every_setting_survives_one_reload_together() {
        val repository = reloaded()
        repository.setInstrument(Instrument.SOPRANO_SAX)
        repository.setNoteNaming(NoteNaming.GERMAN)
        repository.setNoteDisplay(NoteDisplay.RANDOM)
        repository.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.BOTH)
        repository.setCountInBeats(2)
        repository.setCountInClick(false)

        assertEquals(repository.settings.value, reloaded().settings.value)
    }

    @Test
    fun a_value_this_version_does_not_know_falls_back_to_the_default() {
        // Everything localStorage holds is a string, so this also covers the two settings that are
        // not: a count-in that is not a number and a click that is not a boolean read as absent.
        localStorage.setItem(StoredSettings.KEY_INSTRUMENT, "SUBCONTRABASS_SAX")
        localStorage.setItem(StoredSettings.KEY_NOTE_NAMING, "")
        localStorage.setItem(StoredSettings.KEY_NOTE_DISPLAY, "TABLATURE")
        localStorage.setItem(StoredSettings.overrideKey(TrainingTool.LONG_TONES), "NEUMES")
        localStorage.setItem(StoredSettings.KEY_COUNT_IN_BEATS, "eight")
        localStorage.setItem(StoredSettings.KEY_COUNT_IN_CLICK, "yes")

        val restored = reloaded().settings.value
        assertEquals(AppSettings(), restored)
    }

    @Test
    fun a_negative_count_in_reads_as_none() {
        localStorage.setItem(StoredSettings.KEY_COUNT_IN_BEATS, "-4")

        assertEquals(0, reloaded().settings.value.countInBeats)
    }
}

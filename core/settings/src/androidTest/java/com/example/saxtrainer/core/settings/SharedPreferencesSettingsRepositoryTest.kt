package com.example.saxtrainer.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.saxtrainer.core.music.NoteNaming
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole of the app's persistence, tested against real SharedPreferences.
 *
 * It is an instrumented test rather than a unit test because SharedPreferences is the thing under
 * test: a fake would only prove that the fake works. What a cold start does is read the file with a
 * *fresh* repository instance, so that is exactly what [restarted] does - nothing here asserts
 * against the instance that did the writing.
 */
@RunWith(AndroidJUnit4::class)
class SharedPreferencesSettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A repository reading the same file from scratch, which is what launching the app does. */
    private fun restarted() = SharedPreferencesSettingsRepository(context)

    private fun preferences() = context.getSharedPreferences(
        SharedPreferencesSettingsRepository.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    @After
    fun clearStoredSettings() {
        preferences().edit().clear().commit()
    }

    @Test
    fun an_untouched_install_reads_the_defaults() {
        assertEquals(AppSettings(), restarted().settings.value)
    }

    @Test
    fun the_instrument_survives_a_restart() {
        restarted().setInstrument(Instrument.BARITONE_SAX)

        assertEquals(Instrument.BARITONE_SAX, restarted().settings.value.instrument)
    }

    @Test
    fun the_note_naming_survives_a_restart() {
        restarted().setNoteNaming(NoteNaming.GERMAN)

        assertEquals(NoteNaming.GERMAN, restarted().settings.value.noteNaming)
    }

    @Test
    fun the_shared_note_display_survives_a_restart() {
        restarted().setNoteDisplay(NoteDisplay.STAFF)

        assertEquals(NoteDisplay.STAFF, restarted().settings.value.noteDisplay)
    }

    @Test
    fun the_count_in_survives_a_restart() {
        val repository = restarted()
        repository.setCountInBeats(8)
        repository.setCountInClick(false)

        val restored = restarted().settings.value
        assertEquals(8, restored.countInBeats)
        assertEquals(false, restored.countInClick)
    }

    @Test
    fun no_count_in_survives_a_restart_rather_than_reading_as_the_default() {
        // Zero is a choice the settings screen offers, and it is also the value an Int preference
        // would show if it were missing. Storing it has to be told apart from never storing it.
        restarted().setCountInBeats(0)

        assertEquals(0, restarted().settings.value.countInBeats)
    }

    @Test
    fun each_tool_keeps_its_own_note_display_across_a_restart() {
        val repository = restarted()
        repository.setNoteDisplay(NoteDisplay.NAME)
        repository.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.STAFF)

        val restored = restarted().settings.value
        assertEquals(NoteDisplay.STAFF, restored.noteDisplayFor(TrainingTool.LONG_TONES))
        // The tool without a preference of its own still follows the shared default, which means
        // the override was stored under a key of its own rather than over the shared one.
        assertEquals(NoteDisplay.NAME, restored.noteDisplayFor(TrainingTool.FINGERING))
        assertEquals(NoteDisplay.NAME, restored.noteDisplay)
    }

    @Test
    fun every_setting_survives_one_restart_together() {
        val repository = restarted()
        repository.setInstrument(Instrument.SOPRANO_SAX)
        repository.setNoteNaming(NoteNaming.GERMAN)
        repository.setNoteDisplay(NoteDisplay.RANDOM)
        repository.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.BOTH)
        repository.setCountInBeats(2)
        repository.setCountInClick(false)

        assertEquals(repository.settings.value, restarted().settings.value)
    }

    @Test
    fun a_value_this_version_does_not_know_falls_back_to_the_default() {
        // What an install left behind by a newer version, or a hand-edited file, looks like. It has
        // to read as the default rather than throw on the way to the first screen.
        preferences().edit()
            .putString(SharedPreferencesSettingsRepository.KEY_INSTRUMENT, "SUBCONTRABASS_SAX")
            .putString(SharedPreferencesSettingsRepository.KEY_NOTE_NAMING, "")
            .putString(SharedPreferencesSettingsRepository.KEY_NOTE_DISPLAY, "TABLATURE")
            .putString("${SharedPreferencesSettingsRepository.KEY_NOTE_DISPLAY}_LONG_TONES", "NEUMES")
            .putInt(SharedPreferencesSettingsRepository.KEY_COUNT_IN_BEATS, -4)
            .commit()

        val restored = restarted().settings.value
        assertEquals(AppSettings().instrument, restored.instrument)
        assertEquals(AppSettings().noteNaming, restored.noteNaming)
        assertEquals(AppSettings().noteDisplay, restored.noteDisplay)
        assertEquals(emptyMap<TrainingTool, NoteDisplay>(), restored.noteDisplayOverrides)
        assertEquals(0, restored.countInBeats)
    }
}

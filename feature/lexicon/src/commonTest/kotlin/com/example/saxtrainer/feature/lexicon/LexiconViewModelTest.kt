package com.example.saxtrainer.feature.lexicon

import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LexiconViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(settings: AppSettings = AppSettings()) =
        LexiconViewModel(FakeSettingsRepository(settings))

    private val lowest = Note(NoteName.A_SHARP, 3)
    private val highest = Note(NoteName.F_SHARP, 6)

    @Test
    fun `it opens on low C, with its one grip`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(Note(NoteName.C, 4), state.current)
        assertEquals(1, state.fingerings.all.size)
        assertEquals("Standard", state.captionOf(0))
    }

    @Test
    fun `every note the chart has can be looked up, lowest first`() = runTest(dispatcher) {
        val notes = viewModel().uiState.value.notes

        assertEquals(SaxophoneFingerings.supportedNotes.sortedBy { it.midiNumber }, notes)
        assertEquals(lowest, notes.first())
        assertEquals(highest, notes.last())
    }

    @Test
    fun `a black key is shown both ways, a white key once`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.select(Note(NoteName.C_SHARP, 4))
        assertEquals(listOf("C#4", "Db4"), viewModel.uiState.value.spellings.map { it.token })
        assertEquals("C♯4 · D♭4", viewModel.uiState.value.title)
        assertEquals("C♯/D♭", viewModel.uiState.value.chipLabel(Note(NoteName.C_SHARP, 4)))

        viewModel.select(Note(NoteName.D, 4))
        assertEquals(listOf("D4"), viewModel.uiState.value.spellings.map { it.token })
        assertEquals("D", viewModel.uiState.value.chipLabel(Note(NoteName.D, 4)))
    }

    @Test
    fun `B flat brings every grip, each named`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(Note(NoteName.A_SHARP, 4))

        val state = viewModel.uiState.value
        assertEquals(3, state.fingerings.all.size)
        assertEquals(listOf("Standard", "Side B♭", "R1"), state.fingerings.all.indices.map(state::captionOf))
    }

    @Test
    fun `the arrows step a semitone and stop at either end`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.higher()
        assertEquals(Note(NoteName.C_SHARP, 4), viewModel.uiState.value.current)
        viewModel.lower()
        viewModel.lower()
        assertEquals(Note(NoteName.B, 3), viewModel.uiState.value.current)

        viewModel.select(lowest)
        assertFalse(viewModel.uiState.value.canGoLower)
        viewModel.lower()
        assertEquals(lowest, viewModel.uiState.value.current)

        viewModel.select(highest)
        assertFalse(viewModel.uiState.value.canGoHigher)
        assertTrue(viewModel.uiState.value.canGoLower)
        viewModel.higher()
        assertEquals(highest, viewModel.uiState.value.current)
    }

    @Test
    fun `a note the chart does not have is not shown`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(Note(NoteName.C, 8))

        assertEquals(Note(NoteName.C, 4), viewModel.uiState.value.current)
    }

    @Test
    fun `names follow the settings`() = runTest(dispatcher) {
        val viewModel = viewModel(
            AppSettings(noteNaming = NoteNaming.GERMAN, octaveNumbering = OctaveNumbering.SAXOPHONE),
        )
        advanceUntilIdle()
        viewModel.select(Note(NoteName.A_SHARP, 4))

        val state = viewModel.uiState.value
        assertEquals("Ais1 · B1", state.title)
        assertEquals("Ais/B", state.chipLabel(Note(NoteName.A_SHARP, 4)))
    }
}

package com.example.saxtrainer.feature.metronome

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.saxtrainer.core.audio.metronome.ActiveMetronome
import com.example.saxtrainer.core.testing.SilentMetronome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The metronome screen's click, as the notification outside it sees it. */
@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeViewModelTest {

    private val store = ViewModelStore()
    private val engine = SilentMetronome()
    private val active = ActiveMetronome()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun openScreen(): MetronomeViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { MetronomeViewModel(engine = engine, active = active) } },
    )[MetronomeViewModel::class]

    @Test
    fun `opening the screen makes its click the open one`() {
        openScreen()
        assertEquals(engine.state.value.bpm, active.currentSummary?.bpm)
    }

    @Test
    fun `leaving the screen stops the click and lets go of it`() {
        openScreen().toggleRunning()
        assertTrue(engine.state.value.isRunning)

        store.clear()

        assertFalse(engine.state.value.isRunning)
        assertNull(active.currentSummary)
    }

    @Test
    fun `a start from the notification is what the screen shows`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = openScreen()
        backgroundScope.launch { viewModel.uiState.collect { } }

        active.toggle()

        assertTrue(viewModel.uiState.value.isRunning)
    }
}

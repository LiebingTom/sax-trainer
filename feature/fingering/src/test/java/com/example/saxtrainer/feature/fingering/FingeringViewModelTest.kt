package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class FingeringViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = FingeringConfig()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override fun setInstrument(instrument: Instrument) {
            state.value = state.value.copy(instrument = instrument)
        }

        override fun setNoteDisplay(noteDisplay: NoteDisplay) {
            state.value = state.value.copy(noteDisplay = noteDisplay)
        }

        override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
            state.value = state.value.copy(
                noteDisplayOverrides = state.value.noteDisplayOverrides + (tool to noteDisplay),
            )
        }
    }

    /** Plays a steady tone into the trainer; null means silence. */
    private class FakeMicrophone(
        private val frameDurationMs: Long,
        @Volatile var frequencyHz: Double? = null,
    ) : AudioCapture {
        override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
            val frame = AudioFrame(FloatArray(config.frameSize), config.sampleRate)
            var samples = 0L
            while (true) {
                delay(frameDurationMs)
                val tone = frequencyHz
                for (i in frame.samples.indices) {
                    frame.samples[i] = if (tone == null) {
                        0f
                    } else {
                        (0.5 * sin(2.0 * PI * tone * i / config.sampleRate)).toFloat()
                    }
                }
                samples += config.hopSize
                frame.position(startSample = samples, newSamples = config.hopSize)
                emit(AudioCaptureEvent.Frame(frame))
            }
        }
    }

    private fun TestScope.virtualClock() = FingeringSessionClock(
        tickIntervalMs = config.tickIntervalMs,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    private fun TestScope.viewModel(
        seed: Int = 1,
        microphone: AudioCapture? = null,
        settings: SettingsRepository = FakeSettingsRepository(),
    ) = FingeringViewModel(
        settingsRepository = settings,
        audioCapture = microphone,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
        clock = virtualClock(),
    )

    private fun FingeringViewModel.selectOnly(vararg notes: Note) {
        clearNotes()
        notes.forEach(::toggleNote)
    }

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    @Test
    fun `everything the chart knows is offered and selected to begin with`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(SaxophoneFingerings.supportedNotes, state.availableNotes)
        assertEquals(SaxophoneFingerings.supportedNotes.toSet(), state.selectedNotes)
        assertTrue("mixed order is the default", state.isRandom)
        assertFalse("listening is off until asked for", state.checkPlaying)
        assertEquals(CheckMode.SHOW, state.checkMode)
        assertEquals(config.defaultSeconds, state.secondsPerNote)
        assertTrue(state.canStart)
    }

    @Test
    fun `notes can be picked and cleared`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.clearNotes()
        assertFalse(viewModel.uiState.value.canStart)

        viewModel.toggleNote(d4)
        assertEquals(setOf(d4), viewModel.uiState.value.selectedNotes)

        viewModel.toggleNote(d4)
        assertTrue(viewModel.uiState.value.selectedNotes.isEmpty())

        viewModel.selectAllNotes()
        assertEquals(SaxophoneFingerings.supportedNotes.toSet(), viewModel.uiState.value.selectedNotes)
    }

    @Test
    fun `starting without notes does nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.clearNotes()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the first note and its fingering are shown at once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)

        viewModel.start()
        advanceTimeBy(10)

        val progress = viewModel.uiState.value.progress!!
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(c4, progress.currentNote)
        assertEquals(SaxophoneFingerings.fingeringFor(c4), progress.currentFingering)
        assertEquals(d4, progress.nextNote)
        assertEquals(1, progress.displayPosition)
        assertEquals(3, progress.noteCount)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `notes advance on the exercise clock`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(3)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(3_000)
        assertEquals(d4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(3_000)
        assertEquals(e4, viewModel.uiState.value.progress!!.currentNote)
        assertNull("nothing follows the last note", viewModel.uiState.value.progress!!.nextNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `remaining time counts down within a note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setSecondsPerNote(5)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(5f, viewModel.uiState.value.progress!!.remainingSeconds, 0.1f)

        advanceTimeBy(2_000)
        viewModel.uiState.value.progress!!.let {
            assertEquals(3f, it.remainingSeconds, 0.1f)
            assertEquals(0.4f, it.noteFraction, 0.05f)
        }

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the exercise ends after the last note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(3)

        viewModel.start()
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is FingeringPhase.Finished)
        val result = (phase as FingeringPhase.Finished).result
        assertTrue(result.completed)
        assertEquals(listOf(c4, d4), result.notes.map { it.note })
        assertFalse("nothing was listened to", result.checked)
        assertEquals(0, result.correctCount)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `stopping early keeps the notes already practised`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(3)

        viewModel.start()
        advanceTimeBy(4_000)
        viewModel.stop()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertFalse(result.completed)
        assertEquals(listOf(c4), result.notes.map { it.note })
        assertEquals(3, result.plannedNoteCount)
    }

    @Test
    fun `nothing advances while the clock stands still`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)

        viewModel.start()
        advanceTimeBy(10)
        val first = viewModel.uiState.value.progress!!

        assertEquals(first, viewModel.uiState.value.progress)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the setup is locked while an exercise runs`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(5)

        viewModel.start()
        advanceTimeBy(100)

        viewModel.toggleNote(e4)
        viewModel.selectAllNotes()
        viewModel.setSecondsPerNote(8)
        viewModel.setRandomOrder(true)
        viewModel.setCheckPlaying(true)

        val state = viewModel.uiState.value
        assertEquals(setOf(c4, d4), state.selectedNotes)
        assertEquals(5, state.secondsPerNote)
        assertFalse(state.isRandom)
        assertFalse(state.checkPlaying)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `reset returns to the setup screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setSecondsPerNote(3)

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.phase is FingeringPhase.Finished)

        viewModel.reset()
        advanceUntilIdle()

        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `mixed order shuffles the same selection differently`() = runTest(dispatcher) {
        fun run(seed: Int): List<Note> {
            val viewModel = viewModel(seed = seed)
            viewModel.setSecondsPerNote(3)
            viewModel.start()
            advanceUntilIdle()
            val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
            return result.notes.map { it.note }
        }

        val first = run(seed = 1)
        val second = run(seed = 2)

        assertEquals(SaxophoneFingerings.supportedNotes.toSet(), first.toSet())
        assertTrue("two seeds should not give the same order", first != second)
    }

    // --- listening ---------------------------------------------------------------------------

    @Test
    fun `playing the right note marks it correct without moving the exercise on`() =
        runTest(dispatcher) {
            val microphone = FakeMicrophone(frameDurationMs = 40)
            val viewModel = viewModel(microphone = microphone)
            viewModel.selectOnly(c4, d4)
            viewModel.setRandomOrder(false)
            viewModel.setSecondsPerNote(5)
            viewModel.setCheckPlaying(true)
            viewModel.setCheckMode(CheckMode.SHOW)

            microphone.frequencyHz = soundingFrequencyOf(c4)
            viewModel.start()
            advanceTimeBy(1_500)

            val progress = viewModel.uiState.value.progress!!
            assertTrue("the played note should be recognised", progress.isCorrect)
            assertEquals(
                Instrument.TENOR_SAX.transposition.concertFor(c4),
                progress.detectedNote,
            )
            assertEquals("showing only must not skip ahead", c4, progress.currentNote)

            viewModel.stop()
            advanceUntilIdle()
        }

    @Test
    fun `playing the wrong note is not marked correct`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(5)
        viewModel.setCheckPlaying(true)

        microphone.frequencyHz = soundingFrequencyOf(e4) // a different note
        viewModel.start()
        advanceTimeBy(1_500)

        val progress = viewModel.uiState.value.progress!!
        assertFalse(progress.isCorrect)
        assertEquals(Instrument.TENOR_SAX.transposition.concertFor(e4), progress.detectedNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `silence is neither correct nor a detected note`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40, frequencyHz = null)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)
        viewModel.setCheckPlaying(true)

        viewModel.start()
        advanceTimeBy(1_500)

        val progress = viewModel.uiState.value.progress!!
        assertFalse(progress.isCorrect)
        assertNull(progress.detectedNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `advance mode moves on early once the note is held`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(8)
        viewModel.setCheckPlaying(true)
        viewModel.setCheckMode(CheckMode.ADVANCE)

        microphone.frequencyHz = soundingFrequencyOf(c4)
        viewModel.start()
        advanceTimeBy(2_000) // far short of the eight second limit

        assertEquals(
            "holding the right note should move the exercise on",
            d4,
            viewModel.uiState.value.progress!!.currentNote,
        )

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `advance mode still waits out the timer when nothing is played`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40, frequencyHz = null)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(3)
        viewModel.setCheckPlaying(true)
        viewModel.setCheckMode(CheckMode.ADVANCE)

        viewModel.start()
        advanceTimeBy(1_500)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(2_000)
        assertEquals("the timer is the fallback", d4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the result records which notes were played correctly`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setSecondsPerNote(3)
        viewModel.setCheckPlaying(true)
        viewModel.setCheckMode(CheckMode.SHOW)

        microphone.frequencyHz = soundingFrequencyOf(c4) // only the first note is played
        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertTrue(result.checked)
        assertEquals(listOf(c4, d4), result.notes.map { it.note })
        assertEquals(listOf(true, false), result.notes.map { it.playedCorrectly })
        assertEquals(1, result.correctCount)
    }

    @Test
    fun `a refused microphone turns checking off and leaves the exercise usable`() =
        runTest(dispatcher) {
            val viewModel = viewModel(microphone = FakeMicrophone(frameDurationMs = 40))
            viewModel.setCheckPlaying(true)

            viewModel.onMicrophoneUnavailable()

            assertFalse(viewModel.uiState.value.checkPlaying)
            assertTrue(viewModel.uiState.value.checkUnavailable)
            assertTrue(viewModel.uiState.value.canStart)
        }

    @Test
    fun `without a microphone the trainer never claims to be listening`() = runTest(dispatcher) {
        val viewModel = viewModel(microphone = null)
        viewModel.selectOnly(c4)
        viewModel.setSecondsPerNote(3)
        viewModel.setCheckPlaying(true)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertFalse(result.checked)
        assertEquals(0, result.correctCount)
    }

    // --- note display ------------------------------------------------------------------------

    @Test
    fun `the shared default decides how notes are shown`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplay(NoteDisplay.NAME)

        assertEquals(NoteDisplay.NAME, viewModel(settings = settings).uiState.value.noteDisplay)
    }

    @Test
    fun `this tool can be set differently without touching the default`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)

        viewModel.setNoteDisplay(NoteDisplay.STAFF)
        advanceUntilIdle()

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
        assertEquals(
            "the shared default is left alone",
            NoteDisplay.BOTH,
            settings.settings.value.noteDisplay,
        )
        assertEquals(
            NoteDisplay.BOTH,
            settings.settings.value.noteDisplayFor(TrainingTool.LONG_TONES),
        )
    }
}

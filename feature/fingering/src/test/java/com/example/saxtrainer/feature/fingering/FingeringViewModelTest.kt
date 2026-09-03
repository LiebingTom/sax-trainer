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
        preset: FingeringPreset? = null,
    ) = FingeringViewModel(
        settingsRepository = settings,
        audioCapture = microphone,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
        clock = virtualClock(),
    )

    /** Puts exactly these notes into the custom set and switches to it, as the dialog would. */
    private fun FingeringViewModel.selectOnly(vararg notes: Note) {
        editCustomNotes()
        clearCustomNotes()
        notes.forEach(::toggleCustomNote)
        applyCustomNotes()
    }

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    @Test
    fun `the beginner set is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(SaxophoneFingerings.supportedNotes, state.availableNotes)
        assertEquals(NoteSet.BEGINNER, state.noteSet)
        assertEquals(
            "the beginner set leaves out the sharps and flats",
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            state.activeNotes.map { it.label },
        )
        assertTrue("mixed order is the default", state.isRandom)
        assertFalse("listening is off until asked for", state.checkPlaying)
        assertEquals(CheckMode.SHOW, state.checkMode)
        assertEquals(config.defaultSeconds, state.secondsPerNote)
        assertTrue(state.canStart)
    }

    @Test
    fun `choosing a ready made set uses it straight away`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.selectNoteSet(NoteSet.BEGINNER_PLUS)

        val state = viewModel.uiState.value
        assertEquals(NoteSet.BEGINNER_PLUS, state.noteSet)
        assertEquals(
            "the beginner sets stay small even though the chart covers the whole instrument",
            listOf("C4", "D4", "E4", "F4", "F#4", "G4", "A4", "A#4", "B4", "C5"),
            state.activeNotes.map { it.label },
        )
        assertTrue(state.canStart)
    }

    @Test
    fun `the custom set holds its own notes`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.selectOnly(c4, e4)
        assertEquals(NoteSet.CUSTOM, viewModel.uiState.value.noteSet)
        assertEquals(listOf(c4, e4), viewModel.uiState.value.activeNotes)

        // Switching away and back keeps what was chosen.
        viewModel.selectNoteSet(NoteSet.BEGINNER)
        assertEquals(setOf(c4, e4), viewModel.uiState.value.customNotes)

        viewModel.selectNoteSet(NoteSet.CUSTOM)
        assertEquals(listOf(c4, e4), viewModel.uiState.value.activeNotes)
    }

    @Test
    fun `the dialog only changes anything when it is applied`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.editCustomNotes()
        assertTrue(viewModel.uiState.value.isEditingCustomNotes)
        viewModel.toggleCustomNote(e4)
        viewModel.toggleCustomNote(c4)

        assertEquals(
            "the stored selection is untouched while editing",
            setOf(c4, d4),
            viewModel.uiState.value.customNotes,
        )

        viewModel.applyCustomNotes()
        assertEquals(setOf(d4, e4), viewModel.uiState.value.customNotes)
        assertEquals(listOf(d4, e4), viewModel.uiState.value.activeNotes)
        assertFalse(viewModel.uiState.value.isEditingCustomNotes)
    }

    @Test
    fun `cancelling the dialog restores the previous selection`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.editCustomNotes()
        viewModel.clearCustomNotes()
        viewModel.toggleCustomNote(e4)
        viewModel.cancelCustomNotes()

        assertFalse(viewModel.uiState.value.isEditingCustomNotes)
        assertEquals(setOf(c4, d4), viewModel.uiState.value.customNotes)
        assertEquals(listOf(c4, d4), viewModel.uiState.value.activeNotes)
    }

    @Test
    fun `an empty custom set cannot be started`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.editCustomNotes()
        viewModel.clearCustomNotes()
        viewModel.applyCustomNotes()

        assertTrue(viewModel.uiState.value.activeNotes.isEmpty())
        assertFalse(viewModel.uiState.value.canStart)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a ready made set gets the player going again after an empty custom set`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.editCustomNotes()
            viewModel.clearCustomNotes()
            viewModel.applyCustomNotes()

            viewModel.selectNoteSet(NoteSet.BEGINNER)

            assertTrue(viewModel.uiState.value.canStart)
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

        viewModel.selectNoteSet(NoteSet.BEGINNER_PLUS)
        viewModel.editCustomNotes()
        viewModel.setSecondsPerNote(8)
        viewModel.setRandomOrder(true)
        viewModel.setCheckPlaying(true)

        val state = viewModel.uiState.value
        assertEquals(NoteSet.CUSTOM, state.noteSet)
        assertEquals(listOf(c4, d4), state.activeNotes)
        assertFalse("the dialog cannot be opened mid exercise", state.isEditingCustomNotes)
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

        assertEquals(
            NoteSets.notesOf(NoteSet.BEGINNER, SaxophoneFingerings, emptySet()).toSet(),
            first.toSet(),
        )
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

    // --- ready-made exercises ------------------------------------------------------------------

    @Test
    fun `a preset decides the whole exercise`() = runTest(dispatcher) {
        val state = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 8,
                rounds = 2,
                checkMode = CheckMode.ADVANCE,
            ),
        ).uiState.value

        assertEquals(listOf(c4, d4, e4), state.activeNotes)
        assertEquals(NoteOrder.ORDERED, state.order)
        assertEquals(8, state.secondsPerNote)
        assertEquals(2, state.rounds)
        assertEquals(CheckMode.ADVANCE, state.checkMode)
        assertTrue(state.isPreconfigured)
        assertTrue(state.canStart)
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = runTest(dispatcher) {
        // Well below the lowest note a tenor saxophone has.
        val unplayable = Note(NoteName.C, 2)

        val state = viewModel(preset = FingeringPreset(notes = listOf(c4, unplayable))).uiState.value

        assertEquals(listOf(c4), state.activeNotes)
    }

    @Test
    fun `the rounds a preset asks for are all played`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 3,
                rounds = 2,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        assertEquals(4, viewModel.uiState.value.progress!!.noteCount)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `auto start is offered once and then never again`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = FingeringPreset(notes = listOf(c4)))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse("a rotation must not restart the exercise", viewModel.consumeAutoStart())
    }

    @Test
    fun `a preset that does not start by itself is never offered`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = FingeringPreset(notes = listOf(c4), autoStart = false))

        assertFalse(viewModel.consumeAutoStart())
        assertTrue("it waits on the Start button", viewModel.uiState.value.phase is FingeringPhase.Idle)
    }

    @Test
    fun `a freely configured run is never offered an auto start`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertFalse(viewModel.consumeAutoStart())
        assertFalse(viewModel.uiState.value.isPreconfigured)
    }

    @Test
    fun `a preset fixes the presentation without touching the saved preference`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository()
            settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)

            val viewModel = viewModel(
                settings = settings,
                preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
            )
            advanceUntilIdle()

            assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
            assertEquals(
                "the player's own choice is untouched",
                NoteDisplay.NAME,
                settings.settings.value.noteDisplayFor(TrainingTool.FINGERING),
            )
        }

    @Test
    fun `a settings change cannot overwrite what a preset fixed`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(
            settings = settings,
            preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
        )
        advanceUntilIdle()

        settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)
        advanceUntilIdle()

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
    }

    @Test
    fun `a preset that says nothing about presentation follows the player`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)

        val viewModel = viewModel(settings = settings, preset = FingeringPreset(notes = listOf(c4)))
        advanceUntilIdle()

        assertEquals(NoteDisplay.NAME, viewModel.uiState.value.noteDisplay)
    }

    @Test
    fun `a preset cannot switch on listening without a microphone`() = runTest(dispatcher) {
        val state = viewModel(
            microphone = null,
            preset = FingeringPreset(notes = listOf(c4), checkPlaying = true),
        ).uiState.value

        assertFalse(state.checkPlaying)
    }

    // --- how each note is presented -------------------------------------------------------------

    @Test
    fun `a random presentation is resolved before it reaches the screen`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 3,
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(
            "the setting itself is untouched",
            NoteDisplay.RANDOM,
            viewModel.uiState.value.noteDisplay,
        )
        assertTrue(
            "the screen was handed ${progress.noteDisplay}",
            progress.noteDisplay in listOf(NoteDisplay.NAME, NoteDisplay.STAFF),
        )

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note keeps its presentation while it is on screen`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 8,
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)
        val first = viewModel.uiState.value.progress!!

        // The progress is republished every tick; the note must not flicker between the two.
        advanceTimeBy(4_000)
        val stillFirst = viewModel.uiState.value.progress!!

        assertEquals(first.currentNote, stillFirst.currentNote)
        assertEquals(first.noteDisplay, stillFirst.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the preview shows what the next note will actually look like`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 3,
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)
        val promised = viewModel.uiState.value.progress!!.nextNoteDisplay

        advanceTimeBy(3_000)
        val arrived = viewModel.uiState.value.progress!!

        assertEquals(d4, arrived.currentNote)
        assertEquals(promised, arrived.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `both presentations turn up over a long enough exercise`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                secondsPerNote = 3,
                rounds = 8,
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        val seen = mutableSetOf<NoteDisplay>()
        repeat(24) {
            viewModel.uiState.value.progress?.let { seen += it.noteDisplay }
            advanceTimeBy(3_000)
        }

        assertEquals(setOf(NoteDisplay.NAME, NoteDisplay.STAFF), seen)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a fixed presentation is passed straight through`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
        )

        viewModel.start()
        advanceTimeBy(10)

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.progress!!.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `choosing a fixed presentation leaves the note order alone`() = runTest(dispatcher) {
        // The display schedule shares the exercise's generator, so it must not draw from it unless
        // the presentation is actually left to chance.
        fun orderWith(display: NoteDisplay): List<Note> {
            val viewModel = viewModel(
                preset = FingeringPreset(
                    notes = listOf(c4, d4, e4),
                    secondsPerNote = 3,
                    rounds = 2,
                    noteDisplay = display,
                ),
            )
            viewModel.start()
            advanceTimeBy(10)
            val notes = mutableListOf<Note>()
            repeat(6) {
                viewModel.uiState.value.progress?.let { notes += it.currentNote }
                advanceTimeBy(3_000)
            }
            viewModel.stop()
            return notes
        }

        assertEquals(orderWith(NoteDisplay.NAME), orderWith(NoteDisplay.BOTH))
        advanceUntilIdle()
    }
}

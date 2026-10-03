package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup
import kotlin.test.assertNull
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class LongToneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = LongToneConfig()
    private val frameDurationMs = 1_000L * config.capture.hopSize / config.capture.sampleRate
    // Two quiet lead-in beats and one counted bar of 4/4, at the default 60 BPM.
    private val countInMs = 6_000L

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

        /**
     * Emits silent frames in real time (virtual time here), carrying the same sample counters a real
     * recorder would, and records whether the flow released its resources.
     */
    private class FakeAudioCapture(
        private val frameDurationMs: Long,
        private val failWith: AudioCaptureError? = null,
        private val maxFrames: Int = Int.MAX_VALUE,
    ) : AudioCapture {
        var released = false
            private set

        override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
            try {
                if (failWith != null) {
                    emit(AudioCaptureEvent.Failed(failWith))
                    return@flow
                }
                val frame = AudioFrame(FloatArray(config.frameSize), config.sampleRate)
                var emitted = 0
                var samples = 0L
                while (emitted < maxFrames) {
                    delay(frameDurationMs)
                    val newSamples = if (emitted == 0) config.frameSize else config.hopSize
                    samples += newSamples
                    frame.position(
                        startSample = samples - config.frameSize,
                        newSamples = newSamples,
                    )
                    emitted++
                    emit(AudioCaptureEvent.Frame(frame))
                }
            } finally {
                released = true
            }
        }
    }

    private fun TestScope.viewModel(
        capture: AudioCapture,
        // Concert pitch, so the notes chosen are the notes listened for and a test can say either.
        settings: SettingsRepository = FakeSettingsRepository(
            AppSettings(countInBeats = 0, instrument = Instrument.CONCERT),
        ),
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
        preset: LongTonePreset? = null,
        seed: Int = 1,
        // The player cannot choose the tempo any more; a test still can, to hurry a run along.
        fixedTempo: Tempo = config.defaultTempo,
    ) = LongToneViewModel(
        audioCapture = capture,
        settingsRepository = settings,
        exerciseSetups = setups,
        preset = preset,
        config = config.copy(defaultTempo = fixedTempo),
        processingDispatcher = dispatcher,
        random = Random(seed),
        countInClock = MonotonicBeatClock(
            nanoTime = { testScheduler.currentTime * 1_000_000 },
        ),
    )

    private fun written(token: String) = SpelledNote.parse(token)!!

    private fun note(token: String) = written(token).note

    /** Chooses exactly these notes in the note window and applies them, as the player would. */
    private fun LongToneViewModel.selectOnly(vararg tokens: String) {
        openNotePicker()
        updateNotePicker { it.copy(chosen = tokens.map(::written).toSet()) }
        applyNotePicker()
    }

    private fun finishedResult(viewModel: LongToneViewModel): LongToneResult {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is LongTonePhase.Finished, "phase was $phase")
        return phase.result
    }

    @Test
    fun `the default exercise is the beginner's notes, four beats each`() = runTest(dispatcher) {
        val state = viewModel(FakeAudioCapture(frameDurationMs)).uiState.value

        assertEquals(NoteSet.BEGINNER, state.noteSet)
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            state.selection.writtenNotes.map { it.token },
        )
        assertEquals(Beats.of(4), state.selection.beatsPerNote)
        assertEquals(4f, state.selection.secondsPerNote, 1e-3f)
    }

    @Test
    fun `what is listened for is each written note as the instrument sounds it`() = runTest(dispatcher) {
        val tenor = FakeSettingsRepository(AppSettings(countInBeats = 0, instrument = Instrument.TENOR_SAX))
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings = tenor)
        viewModel.selectOnly("C5", "Eb5")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // A tenor sounds a ninth below what is written.
        assertEquals(listOf(note("Bb3"), note("Db4")), state.concertNotes)
        assertEquals(written("Eb5"), state.writtenOf(note("Db4")), "named as chosen, not as a D sharp")
    }

    @Test
    fun `a single note exercise completes after its musical length in audio time`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(frameDurationMs)
            val viewModel = viewModel(capture)
            viewModel.selectOnly("A4")

            viewModel.start()
            advanceUntilIdle()

            val result = finishedResult(viewModel)
            assertTrue(result.completed)
            assertEquals(1, result.steps.size)
            assertEquals(Note(NoteName.A, 4), result.steps.single().note)
            assertTrue(result.heldSeconds >= 4f, "held ${result.heldSeconds} s")
            // One frame of overshoot at most.
            val frameSeconds = config.capture.frameSize.toFloat() / config.capture.sampleRate
            assertTrue(result.heldSeconds < 4f + frameSeconds, "held ${result.heldSeconds} s")
            assertTrue(capture.released, "capture was not released")
        }

    @Test
    fun `a multi note exercise walks every note chosen, lowest first`() = runTest(dispatcher) {
        val capture = FakeAudioCapture(frameDurationMs)
        val viewModel = viewModel(capture)
        viewModel.selectOnly("A4", "C4", "E4", "G4", "D4", "F4")

        viewModel.start()
        advanceUntilIdle()

        val result = finishedResult(viewModel)
        assertTrue(result.completed)
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4").map(::note),
            result.steps.map { it.note },
        )
        assertTrue(result.steps.all { it.completed })
        // Six notes of four beats at 60 BPM.
        assertEquals(24f, result.targetSeconds, 1e-3f)
        assertTrue(result.heldSeconds >= 24f, "played ${result.heldSeconds} s")
        assertTrue(capture.released, "capture was not released")
    }

    @Test
    fun `each note lasts its own musical length`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceUntilIdle()

        val frameSeconds = config.capture.frameSize.toFloat() / config.capture.sampleRate
        finishedResult(viewModel).steps.forEach { step ->
            assertEquals(Beats.of(4), step.targetBeats)
            assertTrue(step.heldSeconds >= 4f, "note held ${step.heldSeconds} s")
            assertTrue(step.heldSeconds < 4f + frameSeconds, "note held ${step.heldSeconds} s")
        }
    }

    @Test
    fun `tempo decides how long a note lasts`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), fixedTempo = Tempo(120))
        viewModel.selectOnly("A4")

        viewModel.start()
        advanceUntilIdle()

        // Four beats at 120 BPM is two seconds.
        val result = finishedResult(viewModel)
        assertTrue(result.heldSeconds >= 2f, "held ${result.heldSeconds} s")
        assertTrue(result.heldSeconds < 2.5f, "held ${result.heldSeconds} s")
    }

    @Test
    fun `progress reports the position and what comes next`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(0, progress.noteIndex)
        assertEquals(1, progress.displayPosition)
        assertEquals(4, progress.noteCount)
        assertEquals(Note(NoteName.A, 4), progress.currentNote)
        assertEquals(listOf(Note(NoteName.B, 4)), progress.upcomingNotes)
        assertEquals(4f, progress.noteTargetSeconds, 1e-3f)

        // Into the second note: the cursor has moved on.
        advanceTimeBy(4_500L)
        val later = viewModel.uiState.value.progress!!
        assertEquals(1, later.noteIndex)
        assertEquals(Note(NoteName.B, 4), later.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    // --- pause ------------------------------------------------------------------------------------

    @Test
    fun `a paused attempt analyses nothing, so its note keeps its place`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)
        viewModel.togglePause()
        val heldAt = viewModel.uiState.value.progress!!.noteElapsedSeconds

        // Frames keep arriving all this time; none of them is analysed.
        advanceTimeBy(30_000L)

        assertTrue(viewModel.uiState.value.isPaused)
        assertTrue(viewModel.uiState.value.phase is LongTonePhase.Running)
        val held = viewModel.uiState.value.progress!!
        assertEquals(0, held.noteIndex)
        assertEquals(heldAt, held.noteElapsedSeconds, 1e-3f)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `continuing carries the sequence on from the note it was held on`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)
        viewModel.togglePause()
        advanceTimeBy(30_000L)
        viewModel.togglePause()

        assertFalse(viewModel.uiState.value.isPaused)

        // Three of the note's four seconds were still to come, and the sequence takes them next.
        advanceTimeBy(2_000L)
        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)

        advanceTimeBy(1_500L)
        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `pausing publishes no result, and stopping still does`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)
        viewModel.togglePause()
        advanceTimeBy(30_000L)

        assertFalse(viewModel.uiState.value.phase is LongTonePhase.Finished)

        viewModel.stop()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is LongTonePhase.Finished)
        assertFalse(viewModel.uiState.value.isPaused)
    }

    @Test
    fun `stopping early keeps the notes played so far including the unfinished one`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(frameDurationMs)
            val viewModel = viewModel(capture)
            viewModel.selectOnly("C4", "E4", "G4", "B4", "D5", "F5")

            viewModel.start()
            advanceTimeBy(countInMs + 10_000L) // two notes of four beats done, into the third
            assertTrue(viewModel.uiState.value.phase is LongTonePhase.Running)

            viewModel.stop()
            advanceUntilIdle()

            val result = finishedResult(viewModel)
            assertFalse(result.completed, "a stopped exercise is not complete")
            assertEquals(2, result.steps.count { it.completed })
            assertEquals(3, result.steps.size)
            assertFalse(result.steps.last().completed)
            assertTrue(capture.released, "capture was not released")
        }

    @Test
    fun `a capture failure produces a failed phase instead of a silent cancellation`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(
                frameDurationMs = frameDurationMs,
                failWith = AudioCaptureError.PermissionDenied,
            )
            val viewModel = viewModel(capture)

            viewModel.start()
            advanceUntilIdle()

            val phase = viewModel.uiState.value.phase
            assertTrue(phase is LongTonePhase.Failed, "phase was $phase")
            assertEquals(
                AudioCaptureError.PermissionDenied,
                phase.error,
            )
            assertTrue(capture.released, "capture was not released")
        }

    @Test
    fun `cancelling during the count-in returns to idle`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))

        viewModel.start()
        assertTrue(viewModel.uiState.value.phase is LongTonePhase.Countdown)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(LongTonePhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the count-in lasts one bar at the trainer's tempo`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), fixedTempo = Tempo(120))

        viewModel.start()
        // A bar is four beats however fast they go by, so the count is always four numbers.
        assertEquals(LongTonePhase.Countdown(4), viewModel.uiState.value.phase)

        // Two quiet lead-in beats come first: at 120 BPM that is a second of standing at four.
        advanceTimeBy(900)
        assertEquals(LongTonePhase.Countdown(4), viewModel.uiState.value.phase)

        // At 120 BPM a beat is half a second.
        advanceTimeBy(700)
        assertEquals(LongTonePhase.Countdown(3), viewModel.uiState.value.phase)

        // Four counted beats is two seconds, after which the exercise itself begins.
        advanceTimeBy(1_500)
        assertTrue(viewModel.uiState.value.phase is LongTonePhase.Running)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `reset returns to idle and clears the measurements`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs, maxFrames = 4))

        viewModel.start()
        advanceUntilIdle()

        viewModel.reset()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LongTonePhase.Idle, state.phase)
        assertEquals(LongToneMeasurements(), state.measurements)
        assertEquals(null, state.progress)
    }

    @Test
    fun `the exercise ends when the capture flow ends on its own`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs, maxFrames = 3))

        viewModel.start()
        advanceUntilIdle()

        assertFalse(finishedResult(viewModel).completed)
    }

    @Test
    fun `the exercise cannot be changed while it is running`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectOnly("A4", "E5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)

        viewModel.selectOnly("C4")
        viewModel.selectBeatsPerNote(Beats.of(8))

        val state = viewModel.uiState.value
        assertEquals(listOf(written("A4"), written("E5")), state.selection.writtenNotes)
        assertEquals(Beats.of(4), state.selection.beatsPerNote)
        assertNull(state.notePicker, "the note window does not open mid-run")

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `choosing notes keeps the note length`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectBeatsPerNote(Beats.of(8))

        viewModel.selectOnly("G4")
        viewModel.openNotePicker()
        viewModel.startNotePickerFrom(NoteSet.CHROMATIC)
        viewModel.applyNotePicker()

        assertEquals(Beats.of(8), viewModel.uiState.value.selection.beatsPerNote)
        assertEquals(NoteSet.CHROMATIC, viewModel.uiState.value.noteSet)
    }

    @Test
    fun `silence still counts towards the exercise but not towards sounding time`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeAudioCapture(frameDurationMs))

            viewModel.start()
            advanceUntilIdle()

            val result = finishedResult(viewModel)
            assertTrue(result.completed)
            assertEquals(0f, result.soundingSeconds, 0f)
            assertEquals(0f, result.steadyFraction, 0f)
            assertEquals(null, result.averagePitchCents)
        }

    @Test
    fun `the shared default decides how notes are shown`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplay(NoteDisplay.STAFF)

        val state = viewModel(FakeAudioCapture(frameDurationMs), settings).uiState.value
        assertEquals(NoteDisplay.STAFF, state.noteDisplay)
    }

    @Test
    fun `this tool can be set differently without touching the default`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)

        viewModel.selectNoteDisplay(NoteDisplay.NAME)
        advanceUntilIdle()

        assertEquals(NoteDisplay.NAME, viewModel.uiState.value.noteDisplay)
        assertEquals(NoteDisplay.STAFF, settings.settings.value.noteDisplay)
        assertEquals(
            NoteDisplay.STAFF,
            settings.settings.value.noteDisplayFor(TrainingTool.FINGERING),
        )
    }

    @Test
    fun `a random presentation is resolved before it reaches the screen`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.RANDOM)
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(NoteDisplay.RANDOM, viewModel.uiState.value.noteDisplay, "the setting itself is untouched")
        assertTrue(
            progress.noteDisplay in listOf(NoteDisplay.NAME, NoteDisplay.STAFF),
            "the screen was handed ${progress.noteDisplay}",
        )
        assertTrue(progress.nextNoteDisplay in listOf(NoteDisplay.NAME, NoteDisplay.STAFF))

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note keeps its presentation while it is held`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.RANDOM)
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 500L)
        val first = viewModel.uiState.value.progress!!

        // A four second note is republished many times over; it must not flicker.
        advanceTimeBy(2_000L)
        val stillFirst = viewModel.uiState.value.progress!!

        assertEquals(first.currentNote, stillFirst.currentNote)
        assertEquals(first.noteDisplay, stillFirst.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the preview shows what the next note will actually look like`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.RANDOM)
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)
        viewModel.selectOnly("A4", "B4", "C5", "D5")

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)
        val promised = viewModel.uiState.value.progress!!.nextNoteDisplay

        advanceTimeBy(4_500L)
        val arrived = viewModel.uiState.value.progress!!

        assertEquals(1, arrived.noteIndex)
        assertEquals(promised, arrived.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a fixed presentation is passed straight through`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.STAFF)
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)

        viewModel.start()
        advanceTimeBy(countInMs + 500L)

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.progress!!.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    // --- remembering the setup ------------------------------------------------------------------

    @Test
    fun `the notes chosen are kept without waiting for a run to be started`() = runTest(dispatcher) {
        // Going back throws the ViewModel away, so a choice not written down by then is lost. The
        // notes are kept as written, so an E flat chosen comes back an E flat.
        val setups = InMemoryExerciseSetupRepository()
        val chosen = viewModel(FakeAudioCapture(frameDurationMs), setups = setups)
        chosen.selectOnly("D4", "Eb4", "F4")
        chosen.selectBeatsPerNote(Beats.of(8))
        chosen.setShowNextNote(false)

        val opened = viewModel(FakeAudioCapture(frameDurationMs), setups = setups).uiState.value

        assertEquals(NoteSet.CUSTOM, opened.noteSet)
        assertEquals(listOf("D4", "Eb4", "F4").map(::written), opened.selection.writtenNotes)
        assertEquals(Beats.of(8), opened.selection.beatsPerNote)
        assertFalse(opened.showNextNote)
    }

    @Test
    fun `a new selection replaces the one before it`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()
        viewModel(FakeAudioCapture(frameDurationMs), setups = setups).selectOnly("D4")
        viewModel(FakeAudioCapture(frameDurationMs), setups = setups).selectOnly("G4")

        val opened = viewModel(FakeAudioCapture(frameDurationMs), setups = setups).uiState.value

        assertEquals(listOf(written("G4")), opened.selection.writtenNotes)
    }

    @Test
    fun `this exercise's setup is its own`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(FakeAudioCapture(frameDurationMs), setups = setups).selectBeatsPerNote(Beats.of(8))

        assertFalse(setups.setupOf(Exercise.LONG_TONES).isEmpty)
        assertTrue(setups.setupOf(Exercise.NOTE_TRAINER).isEmpty, "one exercise, one setup")
    }

    // --- the round trip ---------------------------------------------------------------------

    /**
     * Every choice the setup screen offers survives being written down, turned back into a
     * ready-made exercise, and written down again.
     *
     * A stored setup has two ways out - one opens the setup screen on it, the other starts it - and
     * they have to agree. Where they do not, the difference shows only in an exercise somebody
     * saved, and only once it is started, which is the worst place to find it.
     */
    @Test
    fun `the sax's octave count numbers what is written, never what sounds`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(
            AppSettings(
                countInBeats = 0,
                instrument = Instrument.TENOR_SAX,
                octaveNumbering = OctaveNumbering.SAXOPHONE,
            ),
        )
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings = settings)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        // A tenor's written C5 sounds as the concert B flat a ninth below it.
        val concertBFlat = Note(NoteName.A_SHARP, 3)

        assertEquals("C2", state.writtenLabel(concertBFlat))
        assertEquals("B♭3", state.soundingLabel(concertBFlat))
    }

    @Test
    fun `every choice survives being written down and started again`() = runTest(dispatcher) {
        val configured = viewModel(FakeAudioCapture(frameDurationMs))
        configured.selectOnly("G4", "Ab4", "B4")
        configured.selectBeatsPerNote(Beats.of(8))
        configured.setShowNextNote(false)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(longTonePresetOf(written, editable = true))
        val started = viewModel(FakeAudioCapture(frameDurationMs), preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `a setup that never narrowed the notes down starts on the beginner's notes`() = runTest(dispatcher) {
        // As one written when this exercise was a pattern from a starting note is: neither is read.
        val old = exerciseSetup {
            put("pattern", "single-note")
            put("root_note", 69)
        }
        val preset = assertNotNull(longTonePresetOf(old))

        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            preset.notes.map { it.token },
        )
    }


    /**
     * A practice nobody has touched is still a practice.
     *
     * The setup is written down as it is changed, so an exercise opened and left on its defaults
     * has nothing on record - and "add to your lessons" would have found nothing to save. Asking
     * for it writes it.
     */
    @Test
    fun `an untouched setup is written down when it is asked for`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(FakeAudioCapture(frameDurationMs), setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.LONG_TONES).isEmpty)
    }

}

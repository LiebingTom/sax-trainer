package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.SequenceCatalog
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
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
        settings: SettingsRepository = FakeSettingsRepository(),
        seed: Int = 1,
    ) = LongToneViewModel(
        audioCapture = capture,
        settingsRepository = settings,
        config = config,
        processingDispatcher = dispatcher,
        random = Random(seed),
        countInClock = MonotonicBeatClock(
            nanoTime = { testScheduler.currentTime * 1_000_000 },
        ),
    )

    private fun finishedResult(viewModel: LongToneViewModel): LongToneResult {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is LongTonePhase.Finished, "phase was $phase")
        return phase.result
    }

    @Test
    fun `the default exercise is a single held note`() = runTest(dispatcher) {
        val state = viewModel(FakeAudioCapture(frameDurationMs)).uiState.value

        assertEquals(SequenceCatalog.singleNote, state.selection.pattern)
        assertEquals(Note(NoteName.A, 4), state.selection.rootNote)
        assertEquals(Beats.of(5), state.selection.beatsPerNote)
        assertEquals(5f, state.selection.secondsPerNote, 1e-3f)
    }

    @Test
    fun `a single note exercise completes after its musical length in audio time`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(frameDurationMs)
            val viewModel = viewModel(capture)

            viewModel.start()
            advanceUntilIdle()

            val result = finishedResult(viewModel)
            assertTrue(result.completed)
            assertEquals(1, result.steps.size)
            assertEquals(Note(NoteName.A, 4), result.steps.single().note)
            assertTrue(result.heldSeconds >= 5f, "held ${result.heldSeconds} s")
            // One frame of overshoot at most.
            val frameSeconds = config.capture.frameSize.toFloat() / config.capture.sampleRate
            assertTrue(result.heldSeconds < 5f + frameSeconds, "held ${result.heldSeconds} s")
            assertTrue(capture.released, "capture was not released")
        }

    @Test
    fun `a multi note exercise walks every note of the pattern in order`() = runTest(dispatcher) {
        val capture = FakeAudioCapture(frameDurationMs)
        val viewModel = viewModel(capture)
        viewModel.selectPattern(SequenceCatalog.ascendingThirds)

        viewModel.start()
        advanceUntilIdle()

        val result = finishedResult(viewModel)
        assertTrue(result.completed)
        assertEquals(
            SequenceCatalog.ascendingThirds.notesFrom(Note(NoteName.A, 4)),
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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectPattern(SequenceCatalog.singleNote)
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()

        // Five beats at 120 BPM is 2.5 seconds.
        val result = finishedResult(viewModel)
        assertTrue(result.heldSeconds >= 2.5f, "held ${result.heldSeconds} s")
        assertTrue(result.heldSeconds < 3f, "held ${result.heldSeconds} s")
    }

    @Test
    fun `progress reports the position and what comes next`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
            viewModel.selectPattern(SequenceCatalog.ascendingThirds)

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
    fun `the count-in lasts one bar at the chosen tempo`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.setTempo(120)

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
        viewModel.selectPattern(SequenceCatalog.fifth)

        viewModel.start()
        advanceTimeBy(countInMs + 1_000L)

        viewModel.selectPattern(SequenceCatalog.repeat)
        viewModel.selectRootNote(Note(NoteName.C, 4))
        viewModel.selectBeatsPerNote(Beats.of(8))
        viewModel.setTempo(200)

        val selection = viewModel.uiState.value.selection
        assertEquals(SequenceCatalog.fifth, selection.pattern)
        assertEquals(Note(NoteName.A, 4), selection.rootNote)
        assertEquals(Beats.of(4), selection.beatsPerNote)
        assertEquals(60, selection.tempo.bpm)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `choosing an exercise adopts its natural note length`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))

        viewModel.selectPattern(SequenceCatalog.repeat)
        assertEquals(Beats.of(4), viewModel.uiState.value.selection.beatsPerNote)

        viewModel.selectPattern(SequenceCatalog.singleNote)
        assertEquals(Beats.of(5), viewModel.uiState.value.selection.beatsPerNote)

        // An explicit choice still wins.
        viewModel.selectBeatsPerNote(Beats.of(8))
        assertEquals(Beats.of(8), viewModel.uiState.value.selection.beatsPerNote)
    }

    @Test
    fun `tempo stays inside the supported range`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))

        viewModel.setTempo(1_000)
        assertEquals(config.tempoRange.last, viewModel.uiState.value.selection.tempo.bpm)

        viewModel.setTempo(1)
        assertEquals(config.tempoRange.first, viewModel.uiState.value.selection.tempo.bpm)
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
        assertEquals(NoteDisplay.BOTH, settings.settings.value.noteDisplay)
        assertEquals(
            NoteDisplay.BOTH,
            settings.settings.value.noteDisplayFor(TrainingTool.FINGERING),
        )
    }

    @Test
    fun `a random presentation is resolved before it reaches the screen`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.LONG_TONES, NoteDisplay.RANDOM)
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs), settings)
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
        viewModel.selectPattern(SequenceCatalog.stepwiseUp)

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
}

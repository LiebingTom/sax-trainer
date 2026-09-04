package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Reading a note, playing it, and what the trainer makes of that. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteTrainerViewModelTest {

    /**
     * The fake microphone emits for ever, so an exercise that never ends leaves virtual time with
     * work to do and the test hangs rather than failing. This turns that into a named failure.
     */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val dispatcher = StandardTestDispatcher()

    private val config = NoteTrainerConfig()
    private val frameDurationMs = 40L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsRepository(
        initial: AppSettings = AppSettings(countInBeats = 0),
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state

        override fun setInstrument(instrument: Instrument) {
            state.value = state.value.copy(instrument = instrument)
        }

        override fun setNoteNaming(naming: NoteNaming) {
            state.value = state.value.copy(noteNaming = naming)
        }

        override fun setNoteDisplay(noteDisplay: NoteDisplay) {
            state.value = state.value.copy(noteDisplay = noteDisplay)
        }

        override fun setCountInBeats(beats: Int) {
            state.value = state.value.copy(countInBeats = beats)
        }

        override fun setCountInClick(enabled: Boolean) {
            state.value = state.value.copy(countInClick = enabled)
        }

        override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
            state.value = state.value.copy(
                noteDisplayOverrides = state.value.noteDisplayOverrides + (tool to noteDisplay),
            )
        }
    }

    /**
     * Plays a steady tone into the trainer; null means silence, and [noisy] means a sound with no
     * pitch in it - which the trainer has to tell apart from a wrong note.
     */
    private class FakeMicrophone(
        private val frameDurationMs: Long,
        @Volatile var frequencyHz: Double? = null,
        @Volatile var noisy: Boolean = false,
    ) : AudioCapture {
        override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
            val frame = AudioFrame(FloatArray(config.frameSize), config.sampleRate)
            val noise = Random(7)
            var samples = 0L
            while (true) {
                delay(frameDurationMs)
                val tone = frequencyHz
                for (i in frame.samples.indices) {
                    frame.samples[i] = when {
                        noisy -> (noise.nextDouble() - 0.5).toFloat()
                        tone == null -> 0f
                        else -> (0.5 * sin(2.0 * PI * tone * i / config.sampleRate)).toFloat()
                    }
                }
                samples += config.hopSize
                frame.position(startSample = samples, newSamples = config.hopSize)
                emit(AudioCaptureEvent.Frame(frame))
            }
        }
    }

    private fun TestScope.viewModel(
        microphone: AudioCapture? = FakeMicrophone(frameDurationMs),
        settings: SettingsRepository = FakeSettingsRepository(),
        preset: NoteTrainerPreset? = null,
        seed: Int = 1,
    ) = NoteTrainerViewModel(
        settingsRepository = settings,
        audioCapture = microphone,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
        countInClock = MonotonicBeatClock(nanoTime = { testScheduler.currentTime * 1_000_000 }),
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    /** Puts exactly these notes into the custom set and switches to it, as the dialog would. */
    private fun NoteTrainerViewModel.selectOnly(vararg notes: Note) {
        editCustomNotes()
        clearCustomNotes()
        notes.forEach(::toggleCustomNote)
        applyCustomNotes()
        setRandomOrder(false)
    }

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    /**
     * Runs virtual time on for long enough to settle, and no longer.
     *
     * Deliberately not [advanceUntilIdle]: the fake microphone emits for ever, so "idle" never
     * arrives and a test that expected an exercise to end would hang instead of failing.
     */
    private fun TestScope.settle(millis: Long = 3_000) = advanceTimeBy(millis)

    private fun result(viewModel: NoteTrainerViewModel): NoteTrainerResult {
        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is NoteTrainerPhase.Finished)
        return (phase as NoteTrainerPhase.Finished).result
    }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the beginner set is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(8, state.activeNotes.size)
        assertTrue("mixed order is the default", state.isRandom)
        assertFalse("the fingering is the answer, not the question", state.showFingeringHint)
        assertFalse("the notation is the point", state.showNoteName)
        assertEquals(config.defaultTargetSeconds, state.targetSeconds)
        assertTrue(state.canStart)
    }

    @Test
    fun `without a microphone there is no exercise to run`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onMicrophoneUnavailable()

        assertFalse(viewModel.uiState.value.canStart)

        viewModel.start()

        assertEquals(NoteTrainerPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the setup is not the player's to change once it is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.start()
        advanceTimeBy(200)

        viewModel.setShowFingeringHint(true)
        viewModel.setRounds(3)

        assertFalse(viewModel.uiState.value.showFingeringHint)
        assertEquals(1, viewModel.uiState.value.rounds)

        viewModel.stop()
        settle()
    }

    // --- playing ------------------------------------------------------------------------------

    @Test
    fun `the note is written and the microphone is compared in concert pitch`() =
        runTest(dispatcher) {
            // The tenor sounds a major ninth below what it reads, and the trainer has to know it.
            val microphone = FakeMicrophone(frameDurationMs)
            val viewModel = viewModel(microphone = microphone)
            viewModel.selectOnly(c4)

            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(400)

            assertEquals(Listening.CORRECT, viewModel.uiState.value.progress!!.listening)

            viewModel.stop()
            settle()
        }

    @Test
    fun `playing the note moves on to the next one`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        // Held long enough to count, plus the moment the note stays green.
        advanceTimeBy(1_200)

        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)
        assertEquals(d4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        settle()
    }

    @Test
    fun `the reaction is measured to the first moment the note was heard`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(1_000)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        // A second of looking for it, and the wait that confirms the note is not added on top.
        assertNotNull(attempt.reactionSeconds)
        assertTrue(
            "reaction was ${attempt.reactionSeconds}",
            attempt.reactionSeconds!! in 0.9..1.3,
        )
        assertFalse(attempt.skipped)
    }

    @Test
    fun `a wrong note is counted and named, and the note stays put`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(e4)
        advanceTimeBy(1_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.WRONG_PITCH, progress.listening)
        // Named as the player would read it, not as it sounds in the room.
        assertEquals(e4, progress.detectedNote)
        assertEquals(1, progress.wrongTries)
        assertEquals("a wrong note does not move the exercise on", 0, progress.noteIndex)

        viewModel.stop()
        settle()

        // Stopped in the middle of a note nobody finished: it was neither played nor skipped, so
        // there is nothing honest to record about it.
        assertTrue(result(viewModel).attempts.isEmpty())
    }

    @Test
    fun `the right note after a wrong one still counts`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(e4)
        advanceTimeBy(500)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        assertFalse(attempt.skipped)
        assertEquals(1, attempt.wrongTries)
        assertNotNull(attempt.reactionSeconds)
    }

    @Test
    fun `a sound with no pitch in it is not a wrong try`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs, noisy = true)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(1_500)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.UNCLEAR, progress.listening)
        assertEquals("an unclear sound accuses nobody of anything", 0, progress.wrongTries)
        assertNull(progress.detectedNote)

        viewModel.stop()
        settle()
    }

    @Test
    fun `silence is not a wrong try either`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(2_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.WAITING, progress.listening)
        assertEquals(0, progress.wrongTries)

        viewModel.stop()
        settle()
    }

    @Test
    fun `a note nobody plays is waited for, however long that takes`() = runTest(dispatcher) {
        // No timeout anywhere: needing longer to work out a note is not a failure.
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(5_000)

        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)
        assertTrue(viewModel.uiState.value.phase is NoteTrainerPhase.Running)

        viewModel.stop()
        settle()
    }

    @Test
    fun `skipping gives up on a note without pretending it was played`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(1_000)
        viewModel.skip()
        advanceTimeBy(200)

        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        settle()

        val skipped = result(viewModel).attempts.first()
        assertTrue(skipped.skipped)
        assertNull("a note that was never played has no reaction time", skipped.reactionSeconds)
    }

    // --- pause ---------------------------------------------------------------------------------

    @Test
    fun `a held exercise analyses nothing`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        viewModel.togglePause()
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(5_000)

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals("nothing moved while it was held", 0, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        settle()
    }

    @Test
    fun `continuing puts the note up again rather than counting the pause`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(200)
        viewModel.togglePause()
        advanceTimeBy(5_000)
        viewModel.togglePause()

        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        assertTrue(
            "the pause was counted as thinking time: ${attempt.reactionSeconds}",
            attempt.reactionSeconds!! < 2.0,
        )
    }

    // --- a whole run ---------------------------------------------------------------------------

    @Test
    fun `the exercise ends after the last note`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(1_200)
        microphone.frequencyHz = soundingFrequencyOf(d4)
        settle()

        val result = result(viewModel)
        assertTrue(result.completed)
        assertEquals(listOf(c4, d4), result.attempts.map { it.note })
        assertEquals(2, result.correctCount)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `every round plays every note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRounds(3)

        viewModel.start()
        advanceTimeBy(200)
        repeat(6) {
            viewModel.skip()
            advanceTimeBy(200)
        }
        settle()

        assertEquals(6, result(viewModel).attempts.size)
    }

    @Test
    fun `stopping early keeps the notes that were played`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4, e4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(1_200)

        viewModel.stop()
        settle()

        val result = result(viewModel)
        assertFalse(result.completed)
        assertEquals(1, result.attempts.size)
        assertEquals(3, result.plannedNoteCount)
    }

    // --- the count-in ---------------------------------------------------------------------------

    @Test
    fun `the exercise counts the player in before its first note`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(NoteTrainerPhase.CountIn(4), viewModel.uiState.value.phase)
        assertNull("nothing is asked for during the count", viewModel.uiState.value.progress)

        // Two quiet lead-in beats and one counted one, at the reference sixty.
        advanceTimeBy(3_100)
        assertEquals(NoteTrainerPhase.CountIn(3), viewModel.uiState.value.phase)

        viewModel.stop()
        settle()
    }

    @Test
    fun `stopping during the count-in reports nothing`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(500)
        viewModel.stop()
        settle()

        assertEquals(NoteTrainerPhase.Idle, viewModel.uiState.value.phase)
    }

    // --- ready-made exercises --------------------------------------------------------------------

    @Test
    fun `a preset decides the whole exercise`() = runTest(dispatcher) {
        val state = viewModel(
            preset = NoteTrainerPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                rounds = 2,
                showFingeringHint = true,
                targetSeconds = 8,
            ),
        ).uiState.value

        assertEquals(listOf(c4, d4, e4), state.activeNotes)
        assertEquals(NoteOrder.ORDERED, state.order)
        assertEquals(2, state.rounds)
        assertTrue(state.showFingeringHint)
        assertEquals(8, state.targetSeconds)
        assertTrue(state.isPreconfigured)
    }

    @Test
    fun `the hint shows the fingering while the note is being asked for`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = NoteTrainerPreset(notes = listOf(c4), showFingeringHint = true, autoStart = false),
        )

        viewModel.start()
        advanceTimeBy(200)

        assertNotNull(viewModel.uiState.value.progress!!.fingering)

        viewModel.stop()
        settle()
    }

    @Test
    fun `without the hint the fingering is not offered`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = NoteTrainerPreset(notes = listOf(c4), autoStart = false))

        viewModel.start()
        advanceTimeBy(200)

        assertNull(viewModel.uiState.value.progress!!.fingering)

        viewModel.stop()
        settle()
    }

    @Test
    fun `a preset asks to start exactly once`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = NoteTrainerPreset(notes = listOf(c4)))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse("rotation must not start it again", viewModel.consumeAutoStart())
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = runTest(dispatcher) {
        val state = viewModel(
            preset = NoteTrainerPreset(notes = listOf(c4, Note(NoteName.C, 2))),
        ).uiState.value

        assertEquals(listOf(c4), state.activeNotes)
    }

    // --- the result -------------------------------------------------------------------------------

    @Test
    fun `the middle reaction is reported, not the average`() {
        // One long think should not hide a run that was otherwise quick.
        val result = NoteTrainerResult(
            attempts = listOf(
                NoteAttempt(c4, reactionSeconds = 1.0, wrongTries = 0, skipped = false),
                NoteAttempt(d4, reactionSeconds = 2.0, wrongTries = 0, skipped = false),
                NoteAttempt(e4, reactionSeconds = 30.0, wrongTries = 0, skipped = false),
            ),
            plannedNoteCount = 3,
        )

        assertEquals(2.0, result.medianReactionSeconds!!, 1e-9)
        assertEquals(2, result.withinTarget(4))
    }

    @Test
    fun `a skipped note has no reaction to report`() {
        val result = NoteTrainerResult(
            attempts = listOf(
                NoteAttempt(c4, reactionSeconds = null, wrongTries = 0, skipped = true),
            ),
            plannedNoteCount = 1,
        )

        assertNull(result.medianReactionSeconds)
        assertEquals(0, result.correctCount)
        assertEquals(0, result.withinTarget(4))
    }
}

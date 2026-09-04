package com.example.saxtrainer.feature.sequence

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteTiming
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.NoteNaming
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

/**
 * The trainer, driven by virtual time.
 *
 * No metronome is injected: it renders audio on a real thread, which a unit test has no business
 * starting. Everything the metronome would affect - the count-in - is therefore skipped here, and
 * that is exactly the "click off" path a player also uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SequenceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    /**
     * Most tests are about the notes rather than the count-in, so they start without one; the test
     * that is about it brings its own config.
     */
    private val config = SequenceConfig(countInBars = 0)

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
        override fun setNoteNaming(noteNaming: NoteNaming) {
            state.value = state.value.copy(noteNaming = noteNaming)
        }

        override fun setInstrument(instrument: Instrument) {
            state.value = state.value.copy(instrument = instrument)
        }

        override fun setNoteDisplay(noteDisplay: NoteDisplay) = Unit

        override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) = Unit

        override fun setCountInClick(enabled: Boolean) {
            state.value = state.value.copy(countInClick = enabled)
        }

        override fun setCountInBeats(beats: Int) {
            state.value = state.value.copy(countInBeats = beats)
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
                frame.position(startSample = samples, newSamples = frame.samples.size)
                emit(AudioCaptureEvent.Frame(frame))
                samples += frame.samples.size
            }
        }
    }

    private fun TestScope.virtualClock() = MonotonicBeatClock(
        tickIntervalMs = config.tickIntervalMs,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    /** One pass through, which is what most of these tests want to reason about. */
    private fun SequenceViewModel.playOnce() = setRepeats(1)

    private fun TestScope.viewModel(
        microphone: AudioCapture? = null,
        settings: SettingsRepository = FakeSettingsRepository(),
        preset: SequencePreset? = null,
    ) = SequenceViewModel(
        settingsRepository = settings,
        audioCapture = microphone,
        metronome = null,
        preset = preset,
        config = config,
        processingDispatcher = dispatcher,
        clock = virtualClock(),
    )

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the first scale is ready to go`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(SequenceLibrary.cMajorUp.id, state.selectedId)
        assertEquals(SequenceLibrary.cMajorUp.suggestedTempo, state.tempo)
        assertEquals("five times through by default", 5, state.repeats)
        assertEquals(8 * 5, state.sequence.steps.size)
        assertEquals(8, state.notesPerPass)
        assertFalse(state.addInverse)
        assertTrue(state.canStart)
        assertFalse("nothing is listening until it is asked to", state.checkPitch)
    }

    @Test
    fun `choosing a sequence brings its own suggested tempo`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.selectSequence(SequenceLibrary.dottedSteps.id)

        val state = viewModel.uiState.value
        assertEquals(SequenceLibrary.dottedSteps.id, state.selectedId)
        assertEquals(SequenceLibrary.dottedSteps.suggestedTempo, state.tempo)
        assertEquals(
            SequenceLibrary.dottedSteps.noteCount * state.repeats,
            state.sequence.steps.size,
        )
    }

    @Test
    fun `the tempo stays inside what the app supports`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setTempo(5)
        assertEquals(Tempo.SUPPORTED_RANGE.first, viewModel.uiState.value.tempo.bpm)

        viewModel.setTempo(9_000)
        assertEquals(Tempo.SUPPORTED_RANGE.last, viewModel.uiState.value.tempo.bpm)
    }

    @Test
    fun `checking timing asks for a beat to check against`() = runTest(dispatcher) {
        // With no metronome available the click cannot be switched on, and says so.
        val viewModel = viewModel()

        viewModel.setCheckTiming(true)

        assertTrue(viewModel.uiState.value.checkTiming)
        assertFalse(viewModel.uiState.value.metronomeAvailable)
        assertFalse(viewModel.uiState.value.metronome)
    }

    @Test
    fun `settings are locked while the exercise runs`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.playOnce()
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(10)
        viewModel.setTempo(200)

        assertEquals(60, viewModel.uiState.value.tempo.bpm)

        viewModel.stop()
        advanceUntilIdle()
    }

    // --- progression -------------------------------------------------------------------------

    @Test
    fun `the exercise walks the notes on the clock`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSequence(SequenceLibrary.cMajorUp.id)
        viewModel.playOnce()
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)

        // One beat at sixty is one second, and every note of a scale is a quarter.
        advanceTimeBy(1_000)
        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        advanceTimeBy(2_000)
        assertEquals(3, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a bar is counted in even when there is no click to count it`() = runTest(dispatcher) {
        // The metronome is what a player normally counts with; without one the clock still counts,
        // silently, rather than dropping them straight onto the first note.
        val viewModel = SequenceViewModel(
            settingsRepository = FakeSettingsRepository(),
            audioCapture = null,
            metronome = null,
            config = SequenceConfig(countInBars = 1),
            processingDispatcher = dispatcher,
            clock = virtualClock(),
        )
        viewModel.selectSequence(SequenceLibrary.cMajorUp.id)
        viewModel.playOnce()
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(SequencePhase.CountIn(4), viewModel.uiState.value.phase)

        // Two quiet lead-in beats, then the count proper.
        advanceTimeBy(3_000)
        assertEquals(SequencePhase.CountIn(3), viewModel.uiState.value.phase)

        // Four beats at sixty is four seconds, after which the first note is up.
        advanceTimeBy(3_000)
        assertEquals(SequencePhase.Running, viewModel.uiState.value.phase)
        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the tempo decides how fast, not the sequence`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.playOnce()
        viewModel.setTempo(120)

        viewModel.start()
        advanceTimeBy(10)

        // Twice the speed, so a quarter note lasts half a second.
        advanceTimeBy(500)
        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note lasts as long as it is written`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSequence(SequenceLibrary.dottedSteps.id)
        viewModel.playOnce()
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(10)

        // The first note is a dotted quarter: still there after one second, gone after one and a half.
        advanceTimeBy(1_000)
        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)
        advanceTimeBy(600)
        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the exercise ends after the last note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSequence(SequenceLibrary.upAndBack.id)
        viewModel.playOnce()
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is SequencePhase.Finished)
        val result = (phase as SequencePhase.Finished).result
        assertTrue(result.completed)
        assertEquals(SequenceLibrary.upAndBack.noteCount, result.attempts.size)
        assertNull("nothing was listened to", result.attempts.first().pitchCorrect)
    }

    @Test
    fun `stopping half way keeps what was played`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.playOnce()
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(2_500)
        viewModel.stop()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
        assertFalse(result.completed)
        assertEquals(2, result.attempts.size)
    }

    @Test
    fun `nothing moves without the clock`() = runTest(dispatcher) {
        // Proof that the screen does not drive the timing: no virtual time, no progress.
        val viewModel = viewModel()

        viewModel.start()

        assertNull(viewModel.uiState.value.progress)
        advanceUntilIdle()
    }

    @Test
    fun `reset goes back to the setup screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.playOnce()
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()
        viewModel.reset()

        assertEquals(SequencePhase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.progress)
    }

    // --- listening ---------------------------------------------------------------------------

    @Test
    fun `playing the written note marks it correct`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 20)
        val viewModel = viewModel(microphone = microphone)
        viewModel.playOnce()
        viewModel.setCheckPitch(true)
        viewModel.setTempo(60)

        // A written C4 on a tenor sounds a whole tone and an octave lower.
        microphone.frequencyHz = soundingFrequencyOf(viewModel.uiState.value.sequence.steps[0].note)

        viewModel.start()
        advanceTimeBy(400)

        val progress = viewModel.uiState.value.progress!!
        assertTrue("heard ${progress.detectedNote}", progress.isCorrect)
        assertEquals(viewModel.uiState.value.sequence.steps[0].note, progress.detectedNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `playing the wrong note is not marked correct`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 20)
        val viewModel = viewModel(microphone = microphone)
        viewModel.playOnce()
        viewModel.setCheckPitch(true)
        viewModel.setTempo(60)

        // The fifth note of the scale, played while the first is being asked for.
        microphone.frequencyHz = soundingFrequencyOf(viewModel.uiState.value.sequence.steps[4].note)

        viewModel.start()
        advanceTimeBy(400)

        assertFalse(viewModel.uiState.value.progress!!.isCorrect)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `silence is not a wrong note`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 20, frequencyHz = null)
        val viewModel = viewModel(microphone = microphone)
        viewModel.playOnce()
        viewModel.setCheckPitch(true)
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
        assertTrue(result.checkedPitch)
        assertTrue("nothing was played", result.attempts.all { it.pitchCorrect == false })
        assertEquals(0, result.correctCount)
    }

    @Test
    fun `pitch is not checked when nobody is listening`() = runTest(dispatcher) {
        val viewModel = viewModel(microphone = null)
        viewModel.playOnce()
        viewModel.setCheckPitch(true)
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
        assertFalse(result.checkedPitch)
        assertTrue(result.attempts.all { it.pitchCorrect == null && it.timing == null })
    }

    // --- timing ------------------------------------------------------------------------------

    @Test
    fun `a note played on the beat is on time, and pitch says so separately`() =
        runTest(dispatcher) {
            val microphone = FakeMicrophone(frameDurationMs = 20)
            val viewModel = viewModel(microphone = microphone)
            viewModel.selectSequence(SequenceLibrary.upAndBack.id)
            viewModel.playOnce()
            viewModel.setCheckPitch(true)
            viewModel.setCheckTiming(true)
            viewModel.setTempo(60)

            val steps = viewModel.uiState.value.sequence.steps
            microphone.frequencyHz = soundingFrequencyOf(steps[0].note)

            viewModel.start()
            advanceTimeBy(300)
            microphone.frequencyHz = null
            advanceUntilIdle()

            val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
            val first = result.attempts.first()
            assertEquals(true, first.pitchCorrect)
            assertEquals(NoteTiming.ON_TIME, first.timing)
        }

    @Test
    fun `a note nobody played is not late, it is missing`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 20, frequencyHz = null)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectSequence(SequenceLibrary.upAndBack.id)
        viewModel.playOnce()
        viewModel.setCheckPitch(true)
        viewModel.setCheckTiming(true)
        viewModel.setTempo(120)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
        assertTrue(result.checkedTiming)
        assertTrue(result.attempts.all { it.timing == NoteTiming.NOT_PLAYED })
        assertEquals(0, result.onTimeCount)
    }

    @Test
    fun `a note played well after its beat is late while its pitch stays right`() =
        runTest(dispatcher) {
            val microphone = FakeMicrophone(frameDurationMs = 20)
            val viewModel = viewModel(microphone = microphone)
            viewModel.selectSequence(SequenceLibrary.upAndBack.id)
            viewModel.playOnce()
            viewModel.setCheckPitch(true)
            viewModel.setCheckTiming(true)
            viewModel.setTempo(60)

            val steps = viewModel.uiState.value.sequence.steps

            viewModel.start()
            // The second note is due one second in; play it four tenths late.
            advanceTimeBy(1_400)
            microphone.frequencyHz = soundingFrequencyOf(steps[1].note)
            advanceTimeBy(300)
            microphone.frequencyHz = null
            advanceUntilIdle()

            val second = (viewModel.uiState.value.phase as SequencePhase.Finished)
                .result.attempts[1]
            assertEquals(true, second.pitchCorrect)
            assertEquals(NoteTiming.LATE, second.timing)
        }

    // --- ready-made exercises ------------------------------------------------------------------

    @Test
    fun `a preset decides the whole exercise`() = runTest(dispatcher) {
        val state = viewModel(
            preset = SequencePreset(
                sequenceId = SequenceLibrary.gMajorUp.id,
                tempo = Tempo(70),
                metronome = true,
                checkPitch = false,
                checkTiming = false,
            ),
        ).uiState.value

        assertEquals(SequenceLibrary.gMajorUp.id, state.selectedId)
        assertEquals(70, state.tempo.bpm)
        assertTrue(state.isPreconfigured)
        assertFalse("there is no click to switch on in a test", state.metronome)
    }

    @Test
    fun `a preset cannot switch on listening without a microphone`() = runTest(dispatcher) {
        val state = viewModel(
            microphone = null,
            preset = SequencePreset(
                sequenceId = SequenceLibrary.cMajorUp.id,
                tempo = Tempo(70),
                checkPitch = true,
            ),
        ).uiState.value

        assertFalse(state.checkPitch)
    }

    @Test
    fun `an unknown sequence leaves the default selected`() = runTest(dispatcher) {
        val state = viewModel(
            preset = SequencePreset(sequenceId = "no_such_scale", tempo = Tempo(70)),
        ).uiState.value

        assertEquals(SequenceLibrary.cMajorUp.id, state.selectedId)
    }

    @Test
    fun `auto start is offered once and then never again`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = SequencePreset(sequenceId = SequenceLibrary.cMajorUp.id, tempo = Tempo(70)),
        )

        assertTrue(viewModel.consumeAutoStart())
        assertFalse("a rotation must not restart the exercise", viewModel.consumeAutoStart())
    }

    @Test
    fun `a freely configured run is never offered an auto start`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertFalse(viewModel.consumeAutoStart())
        assertFalse(viewModel.uiState.value.isPreconfigured)
    }

    // --- how much of it -------------------------------------------------------------------------

    @Test
    fun `adding the inverse plays the notes back down`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.playOnce()

        viewModel.setAddInverse(true)

        val state = viewModel.uiState.value
        assertEquals(15, state.sequence.steps.size)
        assertEquals(state.sequence.steps.first().note, state.sequence.steps.last().note)
        assertEquals(15, state.notesPerPass)
    }

    @Test
    fun `repeating multiplies the material`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setRepeats(3)

        val state = viewModel.uiState.value
        assertEquals(3, state.repeats)
        assertEquals(8 * 3, state.sequence.steps.size)
        assertEquals(8, state.notesPerPass)
    }

    @Test
    fun `the two settings compose`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setAddInverse(true)
        viewModel.setRepeats(4)

        assertEquals(15 * 4, viewModel.uiState.value.sequence.steps.size)
    }

    @Test
    fun `a repeat count outside what is offered is pulled back in`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setRepeats(0)
        assertEquals(viewModel.uiState.value.repeatRange.first, viewModel.uiState.value.repeats)

        viewModel.setRepeats(500)
        assertEquals(viewModel.uiState.value.repeatRange.last, viewModel.uiState.value.repeats)
    }

    @Test
    fun `choosing another sequence keeps the shape asked for`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setAddInverse(true)
        viewModel.setRepeats(2)

        viewModel.selectSequence(SequenceLibrary.upAndBack.id)

        val state = viewModel.uiState.value
        assertTrue(state.addInverse)
        assertEquals(2, state.repeats)
        // Five notes, back down to nine, twice over.
        assertEquals(18, state.sequence.steps.size)
    }

    @Test
    fun `every pass is played, and the exercise says which one it is on`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSequence(SequenceLibrary.upAndBack.id)
        viewModel.setRepeats(3)
        viewModel.setTempo(60)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(1, viewModel.uiState.value.progress!!.displayPass)
        assertEquals(3, viewModel.uiState.value.progress!!.passCount)

        // Up and back is six beats, so the second pass begins six seconds in at sixty.
        advanceTimeBy(6_000)
        assertEquals(2, viewModel.uiState.value.progress!!.displayPass)
        advanceTimeBy(6_000)
        assertEquals(3, viewModel.uiState.value.progress!!.displayPass)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the exercise is only over after the last pass`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSequence(SequenceLibrary.upAndBack.id)
        viewModel.setRepeats(3)
        viewModel.setTempo(240)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as SequencePhase.Finished).result
        assertTrue(result.completed)
        assertEquals(SequenceLibrary.upAndBack.noteCount * 3, result.attempts.size)
    }

    @Test
    fun `a preset decides the shape as well as the speed`() = runTest(dispatcher) {
        val state = viewModel(
            preset = SequencePreset(
                sequenceId = SequenceLibrary.cMajorUp.id,
                tempo = Tempo(70),
                addInverse = true,
                repeats = 5,
            ),
        ).uiState.value

        assertTrue(state.addInverse)
        assertEquals(5, state.repeats)
        assertEquals(15 * 5, state.sequence.steps.size)
    }

    @Test
    fun `a preset plays it five times unless it says otherwise`() = runTest(dispatcher) {
        val preset = SequencePreset(sequenceId = SequenceLibrary.cMajorUp.id, tempo = Tempo(70))

        assertEquals(5, preset.repeats)
        assertFalse(preset.addInverse)
    }

    // --- what the notes are called ---------------------------------------------------------------

    @Test
    fun `note names follow the app setting`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        val b = viewModel.uiState.value.sequence.steps[6].note
        assertEquals("B4", viewModel.uiState.value.nameOf(b))

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals("H4", viewModel.uiState.value.nameOf(b))
    }

    @Test
    fun `a flat key is named flat in both systems`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        viewModel.selectSequence(SequenceLibrary.fMajorUp.id)
        advanceUntilIdle()

        val fourth = viewModel.uiState.value.sequence.steps[3].note
        assertEquals("B♭4", viewModel.uiState.value.nameOf(fourth))

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals("B4", viewModel.uiState.value.nameOf(fourth))
    }

    @Test
    fun `a sharp key is named sharp in both systems`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        viewModel.selectSequence(SequenceLibrary.gMajorUp.id)
        advanceUntilIdle()

        val seventh = viewModel.uiState.value.sequence.steps[6].note
        assertEquals("F♯5", viewModel.uiState.value.nameOf(seventh))

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals("Fis5", viewModel.uiState.value.nameOf(seventh))
    }
}

package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LongToneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = LongToneConfig()
    private val frameDurationMs = 1_000L * config.capture.hopSize / config.capture.sampleRate

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

    private fun viewModel(capture: AudioCapture) = LongToneViewModel(
        audioCapture = capture,
        settingsRepository = FakeSettingsRepository(),
        config = config,
        processingDispatcher = dispatcher,
    )

    @Test
    fun `exercise completes after the target duration of audio and releases the microphone`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(frameDurationMs)
            val viewModel = viewModel(capture)
            viewModel.selectDuration(8)

            viewModel.start()
            advanceUntilIdle()

            val phase = viewModel.uiState.value.phase
            assertTrue("phase was $phase", phase is LongTonePhase.Finished)
            val result = (phase as LongTonePhase.Finished).result
            assertTrue(result.completed)
            assertEquals(8, result.targetSeconds)
            assertTrue("held ${result.heldSeconds} s", result.heldSeconds >= 8f)
            // Audio time must not overshoot the target by more than one frame.
            val frameSeconds = config.capture.frameSize.toFloat() / config.capture.sampleRate
            assertTrue("held ${result.heldSeconds} s", result.heldSeconds < 8f + frameSeconds)
            assertTrue("capture was not released", capture.released)
        }

    @Test
    fun `silence still counts towards the attempt but not towards sounding time`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
            viewModel.selectDuration(8)

            viewModel.start()
            advanceUntilIdle()

            val result = (viewModel.uiState.value.phase as LongTonePhase.Finished).result
            assertTrue(result.completed)
            assertEquals(0f, result.soundingSeconds, 0f)
            assertEquals(0f, result.steadyFraction, 0f)
        }

    @Test
    fun `stopping early keeps the partial attempt and releases the microphone`() =
        runTest(dispatcher) {
            val capture = FakeAudioCapture(frameDurationMs)
            val viewModel = viewModel(capture)
            viewModel.selectDuration(30)

            viewModel.start()
            advanceTimeBy(config.countdownSeconds * 1_000L + 3_000L)
            assertTrue(viewModel.uiState.value.phase is LongTonePhase.Running)

            viewModel.stop()
            advanceUntilIdle()

            val phase = viewModel.uiState.value.phase
            assertTrue("phase was $phase", phase is LongTonePhase.Finished)
            val result = (phase as LongTonePhase.Finished).result
            assertFalse("stopped attempt must not be marked complete", result.completed)
            assertTrue("held ${result.heldSeconds} s", result.heldSeconds > 2.5f)
            assertTrue("capture was not released", capture.released)
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
            assertTrue("phase was $phase", phase is LongTonePhase.Failed)
            assertEquals(
                AudioCaptureError.PermissionDenied,
                (phase as LongTonePhase.Failed).error,
            )
            assertTrue("capture was not released", capture.released)
        }

    @Test
    fun `cancelling during the countdown returns to idle`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))

        viewModel.start()
        assertTrue(viewModel.uiState.value.phase is LongTonePhase.Countdown)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(LongTonePhase.Idle, viewModel.uiState.value.phase)
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
        assertEquals(0f, state.elapsedSeconds, 0f)
        assertEquals(LongToneMeasurements(), state.measurements)
    }

    @Test
    fun `the exercise ends when the capture flow ends on its own`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs, maxFrames = 3))
        viewModel.selectDuration(30)

        viewModel.start()
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is LongTonePhase.Finished)
        assertFalse((phase as LongTonePhase.Finished).result.completed)
    }

    @Test
    fun `note and duration cannot be changed while an attempt is running`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAudioCapture(frameDurationMs))
        viewModel.selectDuration(8)

        viewModel.start()
        advanceTimeBy(config.countdownSeconds * 1_000L + 1_000L)
        viewModel.selectDuration(30)

        assertEquals(8, viewModel.uiState.value.targetDurationSeconds)
        viewModel.stop()
        advanceUntilIdle()
    }
}

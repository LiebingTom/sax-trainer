package com.example.saxtrainer.feature.longtones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Drives the long tone exercise: countdown, recording, live feedback and result.
 *
 * Audio is captured and analysed on a background dispatcher; only a small immutable snapshot reaches
 * Compose, at most every [LongToneConfig.uiUpdateIntervalMs], never per audio frame. All signal
 * processing lives in [LongToneProcessor] and `core:dsp` - this class only coordinates.
 *
 * Timing is audio time (samples / sample rate) taken from the captured frames, so neither the
 * displayed duration nor the completion point depends on scheduling or processing latency.
 *
 * Pitch is not validated yet: the target note is training metadata. Adding detection later means
 * extending [LongToneProcessor] and the completion check in [collectFrames], not this class's shape.
 */
class LongToneViewModel(
    private val audioCapture: AudioCapture,
    private val settingsRepository: SettingsRepository,
    private val config: LongToneConfig = LongToneConfig(),
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** How an attempt ended. Every exit path produces exactly one of these. */
    private sealed interface Outcome {
        data object Completed : Outcome

        data object StoppedEarly : Outcome

        data class Failed(val error: AudioCaptureError) : Outcome
    }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<LongToneUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null

    init {
        // Instrument only affects how notes are labelled, so the exercise itself is unaffected.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                )
            }
        }
    }

    fun selectNote(note: Note) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(targetNote = note)
    }

    fun selectDuration(seconds: Int) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(targetDurationSeconds = seconds)
    }

    /** Starts countdown and recording. The caller must have obtained microphone permission. */
    fun start() {
        if (exerciseJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            phase = LongTonePhase.Countdown(config.countdownSeconds),
            elapsedSeconds = 0f,
            measurements = LongToneMeasurements(),
        )
        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise() }
    }

    /**
     * Ends the attempt early and keeps what was achieved. Cancelling the job also cancels the
     * capture flow, which releases the recorder in its own `finally` block.
     */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        // A countdown carries no audio, so it is resolved here: cancelling it may happen before the
        // exercise coroutine has even started, in which case nothing else would update the state.
        if (_uiState.value.phase is LongTonePhase.Countdown) {
            _uiState.value = _uiState.value.copy(phase = LongTonePhase.Idle)
        }
        job?.cancel()
    }

    /** Leaves the result view and returns to the setup state. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(
            phase = LongTonePhase.Idle,
            elapsedSeconds = 0f,
            measurements = LongToneMeasurements(),
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private suspend fun runExercise() {
        val processor = LongToneProcessor(config, config.capture.sampleRate)
        val targetSeconds = _uiState.value.targetDurationSeconds

        try {
            countdown()
            _uiState.value = _uiState.value.copy(phase = LongTonePhase.Running, elapsedSeconds = 0f)
            val outcome = collectFrames(processor, targetSeconds)
            publish(outcome, processor, targetSeconds)
        } catch (cancellation: CancellationException) {
            // The user pressed Stop, the screen went away, or the ViewModel was cleared. What was
            // already played still counts, so report the attempt instead of dropping it silently.
            // reset() has already moved the state on, hence the check on the current phase.
            when (_uiState.value.phase) {
                is LongTonePhase.Running -> publish(Outcome.StoppedEarly, processor, targetSeconds)
                is LongTonePhase.Countdown ->
                    _uiState.value = _uiState.value.copy(phase = LongTonePhase.Idle)

                else -> Unit
            }
            throw cancellation
        }
    }

    private suspend fun countdown() {
        for (second in config.countdownSeconds downTo 1) {
            _uiState.value = _uiState.value.copy(phase = LongTonePhase.Countdown(second))
            delay(COUNTDOWN_STEP_MS)
        }
    }

    /**
     * Collects capture frames until the attempt ends, then stops collecting - which ends the capture
     * flow and releases the microphone. The coroutine never cancels itself.
     */
    private suspend fun collectFrames(
        processor: LongToneProcessor,
        targetSeconds: Int,
    ): Outcome {
        var lastUiUpdateSeconds = -config.uiUpdateIntervalMs / MILLIS_PER_SECOND

        return audioCapture.frames(config.capture)
            .mapNotNull { event ->
                when (event) {
                    is AudioCaptureEvent.Failed -> Outcome.Failed(event.error)

                    is AudioCaptureEvent.Frame -> {
                        val frame = event.frame
                        processor.processFrame(frame.samples, frame.newSamples)
                        val elapsed = processor.analyzedSeconds

                        if (elapsed - lastUiUpdateSeconds >= config.uiUpdateIntervalMs / MILLIS_PER_SECOND) {
                            lastUiUpdateSeconds = elapsed
                            _uiState.value = _uiState.value.copy(
                                elapsedSeconds = elapsed,
                                measurements = processor.measurements(),
                            )
                        }

                        // For this version the target duration is the length of the attempt. When
                        // pitch detection arrives this becomes processor.validHeldSeconds.
                        if (elapsed >= targetSeconds) Outcome.Completed else null
                    }
                }
            }
            .firstOrNull() ?: Outcome.StoppedEarly
    }

    private fun publish(outcome: Outcome, processor: LongToneProcessor, targetSeconds: Int) {
        val state = _uiState.value
        _uiState.value = when (outcome) {
            is Outcome.Failed -> state.copy(phase = LongTonePhase.Failed(outcome.error))

            Outcome.Completed, Outcome.StoppedEarly -> state.copy(
                phase = LongTonePhase.Finished(
                    processor.result(
                        targetSeconds = targetSeconds,
                        completed = outcome == Outcome.Completed,
                    )
                ),
                elapsedSeconds = processor.analyzedSeconds,
            )
        }
    }

    private fun initialState(): LongToneUiState {
        val instrument = settingsRepository.settings.value.instrument
        return LongToneUiState(
            targetNote = config.defaultNote,
            selectableNotes = Note.range(config.lowestNote, config.highestNote),
            transposition = instrument.transposition,
            instrumentName = instrument.displayName,
            targetDurationSeconds = config.defaultDurationSeconds,
            durationOptions = config.durationsSeconds,
        )
    }

    private companion object {
        const val COUNTDOWN_STEP_MS = 1_000L
        const val MILLIS_PER_SECOND = 1_000f
    }
}

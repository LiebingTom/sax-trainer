package com.example.saxtrainer.feature.longtones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.exercise.ExercisePattern
import com.example.saxtrainer.core.exercise.SequenceCatalog
import com.example.saxtrainer.core.exercise.SequenceRunner
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
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
import kotlin.math.roundToInt

/**
 * Drives a long tone exercise: count-in, recording, live feedback and result.
 *
 * Audio is captured and analysed on a background dispatcher; only a small immutable snapshot reaches
 * Compose, at most every [LongToneConfig.uiUpdateIntervalMs], never per audio frame. All signal
 * processing lives in [LongToneProcessor] and `core:dsp` - this class only coordinates.
 *
 * Timing is audio time (samples / sample rate) taken from the captured frames, so neither the
 * displayed duration nor the point at which a note ends depends on scheduling or processing latency.
 * Musical time only enters as a conversion: a note lasts its beats at the chosen tempo.
 *
 * The [SequenceRunner] is a passive cursor - this class decides when a note is over. Pitch is
 * measured and reported but decides nothing: making a note advance only once it is played correctly
 * is a change to [noteFinished] alone.
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
                    noteDisplay = settings.noteDisplayFor(TrainingTool.LONG_TONES),
                )
            }
        }
    }

    fun selectPattern(pattern: ExercisePattern) = updateSelection {
        // Each exercise brings its own natural note length; the player can still override it.
        it.copy(pattern = pattern, beatsPerNote = pattern.defaultBeatsPerNote)
    }

    fun selectRootNote(note: Note) = updateSelection { it.copy(rootNote = note) }

    /** Changes how this tool shows notes; the shared default is left alone. */
    fun selectNoteDisplay(noteDisplay: NoteDisplay) {
        settingsRepository.setNoteDisplayFor(TrainingTool.LONG_TONES, noteDisplay)
    }

    fun selectBeatsPerNote(beats: Beats) = updateSelection { it.copy(beatsPerNote = beats) }

    fun setTempo(bpm: Int) = updateSelection {
        it.copy(tempo = Tempo(bpm.coerceIn(config.tempoRange.first, config.tempoRange.last)))
    }

    /** Starts the count-in and recording. The caller must have obtained microphone permission. */
    fun start() {
        if (exerciseJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            phase = LongTonePhase.Countdown(countInSeconds()),
            progress = null,
            measurements = LongToneMeasurements(),
        )
        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise() }
    }

    /**
     * Ends the exercise early and keeps what was achieved. Cancelling the job also cancels the
     * capture flow, which releases the recorder in its own `finally` block.
     */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        // A count-in carries no audio, so it is resolved here: cancelling it may happen before the
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
            progress = null,
            measurements = LongToneMeasurements(),
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private suspend fun runExercise() {
        val selection = _uiState.value.selection
        val sequence = selection.pattern.build(selection.rootNote, selection.beatsPerNote)
        val runner = SequenceRunner(sequence)
        val processor = LongToneProcessor(config, config.capture.sampleRate)
        val results = mutableListOf<LongToneStepResult>()

        try {
            countIn()
            processor.startStep(runner.currentStep.note.frequencyHz())
            _uiState.value = _uiState.value.copy(
                phase = LongTonePhase.Running,
                progress = progressOf(runner, selection, stepElapsed = 0f, exerciseElapsed = 0f),
            )

            val outcome = collectFrames(runner, processor, selection, results)
            publish(outcome, processor, selection, results)
        } catch (cancellation: CancellationException) {
            // The user pressed Stop, the screen went away, or the ViewModel was cleared. What was
            // already played still counts, so report the attempt instead of dropping it silently.
            // reset() has already moved the state on, hence the check on the current phase.
            when (_uiState.value.phase) {
                is LongTonePhase.Running -> {
                    results += processor.stepResult(runner.currentStep, completed = false)
                    publish(Outcome.StoppedEarly, processor, selection, results)
                }

                is LongTonePhase.Countdown ->
                    _uiState.value = _uiState.value.copy(phase = LongTonePhase.Idle)

                else -> Unit
            }
            throw cancellation
        }
    }

    private suspend fun countIn() {
        for (second in countInSeconds() downTo 1) {
            _uiState.value = _uiState.value.copy(phase = LongTonePhase.Countdown(second))
            delay(COUNT_IN_STEP_MS)
        }
    }

    /**
     * Collects capture frames until the exercise ends, then stops collecting - which ends the
     * capture flow and releases the microphone. The coroutine never cancels itself.
     */
    private suspend fun collectFrames(
        runner: SequenceRunner,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
        results: MutableList<LongToneStepResult>,
    ): Outcome {
        var lastUiUpdateSeconds = -config.uiUpdateIntervalMs / MILLIS_PER_SECOND

        return audioCapture.frames(config.capture)
            .mapNotNull { event ->
                when (event) {
                    is AudioCaptureEvent.Failed -> Outcome.Failed(event.error)

                    is AudioCaptureEvent.Frame -> {
                        val frame = event.frame
                        processor.processFrame(frame.samples, frame.newSamples)

                        if (noteFinished(processor, runner, selection)) {
                            results += processor.stepResult(runner.currentStep, completed = true)
                            if (runner.advance()) {
                                processor.startStep(runner.currentStep.note.frequencyHz())
                                lastUiUpdateSeconds = -config.uiUpdateIntervalMs / MILLIS_PER_SECOND
                                publishProgress(runner, processor, selection)
                                null
                            } else {
                                Outcome.Completed
                            }
                        } else {
                            val elapsed = processor.analyzedSeconds
                            val interval = config.uiUpdateIntervalMs / MILLIS_PER_SECOND
                            if (elapsed - lastUiUpdateSeconds >= interval) {
                                lastUiUpdateSeconds = elapsed
                                publishProgress(runner, processor, selection)
                            }
                            null
                        }
                    }
                }
            }
            .firstOrNull() ?: Outcome.StoppedEarly
    }

    /**
     * A note is over once its musical length has passed in audio time. The check happens per frame,
     * so a note may run up to one frame (about 43 ms) long; that overshoot is measured and reported,
     * never silently dropped from the statistics.
     */
    private fun noteFinished(
        processor: LongToneProcessor,
        runner: SequenceRunner,
        selection: ExerciseSelection,
    ): Boolean = processor.analyzedSeconds >= selection.tempo.secondsFor(runner.currentStep.duration)

    private fun publishProgress(
        runner: SequenceRunner,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
    ) {
        _uiState.value = _uiState.value.copy(
            progress = progressOf(
                runner = runner,
                selection = selection,
                stepElapsed = processor.analyzedSeconds,
                exerciseElapsed = processor.exerciseSeconds,
            ),
            measurements = processor.measurements(),
        )
    }

    private fun progressOf(
        runner: SequenceRunner,
        selection: ExerciseSelection,
        stepElapsed: Float,
        exerciseElapsed: Float,
    ): ExerciseProgress {
        val sequenceProgress = runner.progress
        return ExerciseProgress(
            noteIndex = sequenceProgress.stepIndex,
            noteCount = sequenceProgress.stepCount,
            currentNote = runner.currentStep.note,
            upcomingNotes = runner.upcoming(UPCOMING_NOTES).map { it.note },
            noteElapsedSeconds = stepElapsed,
            noteTargetSeconds = selection.tempo.secondsFor(runner.currentStep.duration).toFloat(),
            exerciseElapsedSeconds = exerciseElapsed,
            exerciseTotalSeconds = selection.totalSeconds,
        )
    }

    private fun publish(
        outcome: Outcome,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
        results: List<LongToneStepResult>,
    ) {
        val state = _uiState.value
        _uiState.value = when (outcome) {
            is Outcome.Failed -> state.copy(phase = LongTonePhase.Failed(outcome.error))

            Outcome.Completed, Outcome.StoppedEarly -> state.copy(
                phase = LongTonePhase.Finished(
                    processor.exerciseResult(
                        steps = results.toList(),
                        targetSeconds = selection.totalSeconds,
                        completed = outcome == Outcome.Completed,
                    )
                ),
            )
        }
    }

    private fun updateSelection(change: (ExerciseSelection) -> ExerciseSelection) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(selection = change(_uiState.value.selection))
    }

    /** One bar of count-in at the chosen tempo, rounded to whole seconds for the display. */
    private fun countInSeconds(): Int {
        val selection = _uiState.value.selection
        val beats = Beats.of(config.countInBars * COUNT_IN_BEATS_PER_BAR)
        return selection.tempo.secondsFor(beats).roundToInt().coerceAtLeast(1)
    }

    private fun initialState(): LongToneUiState {
        val instrument = settingsRepository.settings.value.instrument
        return LongToneUiState(
            selection = ExerciseSelection(
                pattern = config.defaultPattern,
                rootNote = config.defaultRoot,
                beatsPerNote = config.defaultPattern.defaultBeatsPerNote,
                tempo = config.defaultTempo,
            ),
            patterns = SequenceCatalog.patterns,
            selectableNotes = Note.range(config.lowestNote, config.highestNote),
            beatOptions = config.beatChoices,
            tempoRange = config.tempoRange,
            transposition = instrument.transposition,
            instrumentName = instrument.displayName,
            noteDisplay = settingsRepository.settings.value.noteDisplayFor(TrainingTool.LONG_TONES),
            centsDisplayRange = config.centsDisplayRange,
        )
    }

    private companion object {
        const val COUNT_IN_STEP_MS = 1_000L
        const val COUNT_IN_BEATS_PER_BAR = 4
        const val MILLIS_PER_SECOND = 1_000f
        const val UPCOMING_NOTES = 1
    }
}

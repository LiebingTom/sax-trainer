package com.example.saxtrainer.feature.fingering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.SequenceRunner
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs a fingering exercise: show a note with its fingering, hold it for the chosen time, move on.
 *
 * The exercise always runs on its own clock, so it works in silence. Listening is optional: when it
 * is on, the microphone only reports whether the right note is sounding, and in
 * [CheckMode.ADVANCE] it may end a note early - the timer stays as the fallback, so nobody can get
 * stuck on a note they cannot play.
 *
 * Randomisation happens once, when the exercise is generated; [SequenceRunner] then walks a fixed
 * sequence, which keeps it a passive cursor and makes an exercise reproducible from a seed.
 */
class FingeringViewModel(
    private val settingsRepository: SettingsRepository,
    private val audioCapture: AudioCapture? = null,
    private val config: FingeringConfig = FingeringConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: FingeringSessionClock = FingeringSessionClock(config.tickIntervalMs),
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<FingeringUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                    noteDisplay = settings.noteDisplayFor(TrainingTool.FINGERING),
                )
            }
        }
    }

    fun toggleNote(note: Note) {
        if (_uiState.value.isRunning) return
        val selected = _uiState.value.selectedNotes
        _uiState.value = _uiState.value.copy(
            selectedNotes = if (note in selected) selected - note else selected + note,
        )
    }

    fun selectAllNotes() = updateSelection { it.availableNotes.toSet() }

    fun clearNotes() = updateSelection { emptySet() }

    fun setRandomOrder(random: Boolean) = updateSetup {
        it.copy(order = if (random) NoteOrder.RANDOM else NoteOrder.ORDERED)
    }

    fun setSecondsPerNote(seconds: Int) = updateSetup { it.copy(secondsPerNote = seconds) }

    fun setCheckPlaying(enabled: Boolean) = updateSetup {
        it.copy(checkPlaying = enabled, checkUnavailable = false)
    }

    fun setCheckMode(mode: CheckMode) = updateSetup { it.copy(checkMode = mode) }

    /** Changes how this tool shows notes; the shared default is left alone. */
    fun setNoteDisplay(noteDisplay: NoteDisplay) {
        settingsRepository.setNoteDisplayFor(TrainingTool.FINGERING, noteDisplay)
    }

    /**
     * Called by the screen when the microphone was asked for and refused: the exercise carries on
     * without listening rather than refusing to start.
     */
    fun onMicrophoneUnavailable() {
        _uiState.value = _uiState.value.copy(checkPlaying = false, checkUnavailable = true)
    }

    fun start() {
        if (exerciseJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canStart) return

        val sequence = FingeringExerciseGenerator.generate(
            notes = state.selectedInPitchOrder,
            order = state.order,
            secondsPerNote = state.secondsPerNote,
            rounds = config.rounds,
            tempo = config.tempo,
            random = random,
        )

        exerciseJob = viewModelScope.launch(processingDispatcher) {
            runExercise(SequenceRunner(sequence))
        }
    }

    /** Ends the exercise early and keeps the notes that were practised. */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        job?.cancel()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(phase = FingeringPhase.Idle, progress = null)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private suspend fun runExercise(runner: SequenceRunner) = coroutineScope {
        val practised = mutableListOf<PractisedNote>()
        val noteCount = runner.sequence.steps.size
        val listening = _uiState.value.checkPlaying && audioCapture != null
        val heard = MutableStateFlow<DetectedNote?>(null)

        // Listening runs beside the clock and only reports; it never drives the exercise on its own.
        val listenJob = if (listening) {
            launch {
                NoteListener(audioCapture!!, config.capture, config.listening)
                    .detectedNotes()
                    .collect { heard.value = it }
            }
        } else {
            null
        }

        var noteStartSeconds = 0f
        var correctSinceSeconds: Float? = null
        var playedCorrectly = false

        try {
            publishProgress(runner, elapsedInNote = 0f, detected = null, correct = false)
            _uiState.value = _uiState.value.copy(phase = FingeringPhase.Running)

            // Collection stops once the last note is over; the coroutine never cancels itself.
            clock.elapsedSeconds()
                .mapNotNull { elapsed ->
                    val noteSeconds = noteSecondsOf(runner)
                    val elapsedInNote = elapsed - noteStartSeconds

                    val expected = _uiState.value.soundingNoteOf(runner.currentStep.note)
                    val detected = if (listening) heard.value else null
                    val correct = detected != null && detected.note == expected
                    if (correct) {
                        playedCorrectly = true
                        if (correctSinceSeconds == null) correctSinceSeconds = elapsedInNote
                    } else {
                        correctSinceSeconds = null
                    }

                    val heldCorrectSeconds = correctSinceSeconds?.let { elapsedInNote - it } ?: 0f
                    val playedLongEnough = listening &&
                        _uiState.value.checkMode == CheckMode.ADVANCE &&
                        heldCorrectSeconds >= config.requiredCorrectSeconds

                    if (elapsedInNote < noteSeconds && !playedLongEnough) {
                        publishProgress(runner, elapsedInNote, detected?.note, correct)
                        null
                    } else {
                        practised += PractisedNote(runner.currentStep.note, playedCorrectly)
                        // An early finish still consumes the whole note, so later notes keep their
                        // full time rather than inheriting the leftover.
                        noteStartSeconds += maxOf(noteSeconds, elapsedInNote)
                        playedCorrectly = false
                        correctSinceSeconds = null
                        if (runner.advance()) {
                            publishProgress(runner, elapsedInNote = 0f, detected = null, correct = false)
                            null
                        } else {
                            EXERCISE_OVER
                        }
                    }
                }
                .firstOrNull()

            finish(practised, noteCount, listening)
        } catch (cancellation: CancellationException) {
            // Stopped by the player or by leaving the screen: report what was practised.
            if (_uiState.value.phase is FingeringPhase.Running) {
                finish(practised, noteCount, listening)
            }
            throw cancellation
        } finally {
            listenJob?.cancel()
        }
    }

    private fun finish(practised: List<PractisedNote>, plannedNoteCount: Int, checked: Boolean) {
        _uiState.value = _uiState.value.copy(
            phase = FingeringPhase.Finished(
                FingeringResult(
                    notes = practised.toList(),
                    plannedNoteCount = plannedNoteCount,
                    checked = checked,
                )
            ),
            progress = null,
        )
    }

    private fun publishProgress(
        runner: SequenceRunner,
        elapsedInNote: Float,
        detected: Note?,
        correct: Boolean,
    ) {
        val note = runner.currentStep.note
        val noteSeconds = noteSecondsOf(runner)
        _uiState.value = _uiState.value.copy(
            progress = FingeringProgress(
                noteIndex = runner.currentIndex,
                noteCount = runner.sequence.steps.size,
                currentNote = note,
                currentFingering = config.chart.fingeringFor(note),
                nextNote = runner.upcoming(1).firstOrNull()?.note,
                remainingSeconds = (noteSeconds - elapsedInNote).coerceAtLeast(0f),
                noteSeconds = noteSeconds,
                detectedNote = detected,
                isCorrect = correct,
            ),
        )
    }

    private fun noteSecondsOf(runner: SequenceRunner): Float =
        config.tempo.secondsFor(runner.currentStep.duration).toFloat()

    private fun updateSelection(change: (FingeringUiState) -> Set<Note>) {
        if (_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(selectedNotes = change(_uiState.value))
    }

    private fun updateSetup(change: (FingeringUiState) -> FingeringUiState) {
        if (_uiState.value.isRunning) return
        _uiState.value = change(_uiState.value)
    }

    private fun initialState(): FingeringUiState {
        val notes = config.chart.supportedNotes
        val settings = settingsRepository.settings.value
        return FingeringUiState(
            availableNotes = notes,
            selectedNotes = notes.toSet(),
            order = NoteOrder.RANDOM,
            secondsPerNote = config.defaultSeconds,
            secondsOptions = config.secondsOptions,
            checkPlaying = config.checkPlayingByDefault && audioCapture != null,
            checkMode = config.defaultCheckMode,
            transposition = settings.instrument.transposition,
            instrumentName = settings.instrument.displayName,
            noteDisplay = settings.noteDisplayFor(TrainingTool.FINGERING),
        )
    }

    private companion object {
        /** Marker that ends the tick collection; the value itself carries no meaning. */
        val EXERCISE_OVER = Any()
    }
}

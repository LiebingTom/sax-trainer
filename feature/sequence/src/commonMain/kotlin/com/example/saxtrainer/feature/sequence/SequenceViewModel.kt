package com.example.saxtrainer.feature.sequence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.listening.DetectedNote
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.clickCountIn
import com.example.saxtrainer.core.exercise.BeatClock
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteTiming
import com.example.saxtrainer.core.exercise.SequenceRunner
import com.example.saxtrainer.core.exercise.countIn
import com.example.saxtrainer.core.exercise.leadIn
import com.example.saxtrainer.core.exercise.leadInBeatsFor
import com.example.saxtrainer.core.exercise.endBeatOf
import com.example.saxtrainer.core.exercise.expectedSecondsOf
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository
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
import kotlin.math.abs

/**
 * Runs a sequence exercise: show the music, walk through it in time, and report what was played.
 *
 * The clock never waits. Notes advance at the tempo whatever the player does, which is the only way
 * a timing check can mean anything - and it is also what a metronome does. Pitch and timing are
 * graded afterwards, never used to gate progress.
 *
 * The [SequenceRunner] stays a passive cursor: this class decides when a note is over, from musical
 * position alone. No screen computes a time, and no millisecond appears in a sequence.
 */
class SequenceViewModel(
    private val settingsRepository: SettingsRepository,
    /**
     * What this trainer was last started with. Defaulted so a trainer without one still runs; it
     * simply opens on the plain defaults every time.
     */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val audioCapture: AudioCapture? = null,
    private val metronome: Metronome? = null,
    private val preset: SequencePreset? = null,
    private val config: SequenceConfig = SequenceConfig(),
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: BeatClock = MonotonicBeatClock(config.tickIntervalMs),
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SequenceUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null

    /** A preset that asked to start does so once, not again on every recomposition or rotation. */
    private var autoStartPending = preset?.autoStart == true

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                    noteNaming = settings.noteNaming,
                )
            }
        }
    }

    // --- setup ----------------------------------------------------------------------------------

    fun selectSequence(id: String) = updateSetup { state ->
        val definition = state.library.firstOrNull { it.id == id } ?: return@updateSetup state
        state.copy(selectedId = id, tempo = definition.suggestedTempo).withMaterial()
    }

    /** Play the notes back down to where they started before repeating. */
    fun setAddInverse(enabled: Boolean) = updateSetup {
        it.copy(addInverse = enabled).withMaterial()
    }

    fun setRepeats(times: Int) = updateSetup {
        it.copy(repeats = times.coerceIn(it.repeatRange)).withMaterial()
    }

    fun setTempo(bpm: Int) = updateSetup {
        it.copy(tempo = Tempo(bpm.coerceIn(it.tempoRange)))
    }

    /**
     * A click needs somewhere to sound, exactly as reading a setup back does.
     *
     * Without the gate the switch could be left on where there is no metronome at all, and what was
     * written down would then differ from what any later reading of it produced - see the
     * round-trip test.
     */
    fun setMetronome(enabled: Boolean) = updateSetup {
        it.copy(metronome = enabled && it.metronomeAvailable)
    }

    fun setCheckPitch(enabled: Boolean) = updateSetup {
        it.copy(checkPitch = enabled, checkUnavailable = false)
    }

    /**
     * Timing is measured against the beat, so switching the check on switches the click on with it -
     * asking a player to be on a beat they cannot hear is a trick question. They may turn it off
     * again afterwards.
     */
    fun setCheckTiming(enabled: Boolean) = updateSetup {
        it.copy(
            checkTiming = enabled,
            metronome = if (enabled && it.metronomeAvailable) true else it.metronome,
        )
    }

    /** Called when the microphone was asked for and refused; the exercise carries on without it. */
    fun onMicrophoneUnavailable() {
        _uiState.value = _uiState.value.copy(checkPitch = false, checkUnavailable = true)
    }

    /**
     * Whether the screen should start without being asked, answered true only once. The screen owns
     * the microphone permission flow, so the decision to start is taken there.
     */
    /**
     * Writes the current setup down now, without waiting for it to be changed or started.
     *
     * What the setup screen is showing is a practice whether or not the player has touched a
     * control, so anything outside that wants to keep it - "add to your lessons" - has to be able
     * to ask for it. Nothing else about when a setup is remembered changes: the untouched defaults
     * are only written when somebody actually asks for them.
     */
    fun rememberSetupNow() = rememberSetup(_uiState.value)

    fun consumeAutoStart(): Boolean {
        if (!autoStartPending) return false
        autoStartPending = false
        return true
    }

    // --- session ---------------------------------------------------------------------------------

    fun start() {
        if (exerciseJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canStart) return

        // Already written down as it was chosen; this is the setup as it is actually
        // started.
        rememberSetup(state)

        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise() }
    }

    /** Ends the exercise early and keeps whatever was played. */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        job?.cancel()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(phase = SequencePhase.Idle, progress = null)
    }

    override fun onCleared() {
        stop()
        metronome?.release()
        super.onCleared()
    }

    private suspend fun runExercise() = coroutineScope {
        val state = _uiState.value
        val sequence = state.sequence
        val tempo = state.tempo
        val runner = SequenceRunner(sequence)
        val listening = state.checkPitch && audioCapture != null
        val clicking = state.metronome && metronome != null

        // Where each note was actually attacked, in seconds from the first note.
        val onsets = mutableMapOf<Int, Double>()
        val heard = MutableStateFlow<DetectedNote?>(null)

        val listenJob = if (listening) {
            launch {
                NoteListener(audioCapture, config.capture, config.listening)
                    .detectedNotes()
                    .collect { heard.value = it }
            }
        } else {
            null
        }

        try {
            val countInBeats = config.countInBars * sequence.timeSignature.beatsPerBar
            if (countInBeats > 0) {
                // A quiet moment to get the horn up before the count starts, clicked or not.
                _uiState.value = _uiState.value.copy(phase = SequencePhase.CountIn(countInBeats))
                clock.leadIn(leadInBeatsFor(countInBeats), tempo)
            }

            if (clicking) {
                // The click keeps running through the exercise, so nothing stops it here.
                metronome.clickCountIn(countInBeats, tempo) { beatsRemaining ->
                    _uiState.value = _uiState.value.copy(phase = SequencePhase.CountIn(beatsRemaining))
                }
            } else {
                // No click to lock onto, so the clock counts the same beats out silently rather
                // than dropping the player straight onto the first note.
                clock.countIn(countInBeats, tempo) { beatsRemaining ->
                    _uiState.value = _uiState.value.copy(phase = SequencePhase.CountIn(beatsRemaining))
                }
            }
            publishProgress(runner, sequence, Beats.ZERO, null, onsets)
            _uiState.value = _uiState.value.copy(phase = SequencePhase.Running)

            clock.elapsedBeats(tempo)
                .mapNotNull { elapsed ->
                    if (listening) {
                        recordOnset(runner, sequence, tempo, elapsed, heard.value, onsets)
                    }

                    val noteOver = elapsed >= sequence.endBeatOf(runner.currentIndex)
                    if (!noteOver) {
                        publishProgress(runner, sequence, elapsed, heard.value, onsets)
                        null
                    } else if (runner.advance()) {
                        publishProgress(runner, sequence, elapsed, heard.value, onsets)
                        null
                    } else {
                        EXERCISE_OVER
                    }
                }
                .firstOrNull()

            finish(sequence, tempo, runner.currentIndex + 1, onsets, listening, state.checkTiming)
        } catch (cancellation: CancellationException) {
            // Stopped by the player or by leaving the screen: report what was played.
            if (_uiState.value.isRunning) {
                finish(sequence, tempo, runner.currentIndex, onsets, listening, state.checkTiming)
            }
            throw cancellation
        } finally {
            listenJob?.cancel()
            if (clicking) metronome.stop()
        }
    }

    /**
     * Attributes a heard pitch to the note it most likely belongs to.
     *
     * A player can attack a note a little before or after it is written, so the neighbours are
     * candidates too - that is what makes "early" and "late" different from "wrong note". Only the
     * first attack of a note is kept; holding it does not keep re-attacking it.
     */
    private fun recordOnset(
        runner: SequenceRunner,
        sequence: ToneSequence,
        tempo: Tempo,
        elapsed: Beats,
        detected: DetectedNote?,
        onsets: MutableMap<Int, Double>,
    ) {
        val sounding = detected?.note ?: return
        val nowSeconds = tempo.secondsFor(elapsed)
        val state = _uiState.value

        val candidate = (runner.currentIndex - 1..runner.currentIndex + 1)
            .filter { it in sequence.steps.indices && it !in onsets }
            // A rest has nothing to attribute a pitch to: playing during one is not an early
            // attack on it, it is simply not part of the exercise.
            .filter { sequence.steps[it].note?.let(state::soundingNoteOf) == sounding }
            .filter {
                config.timing.belongsToNote(sequence.expectedSecondsOf(it, tempo), nowSeconds)
            }
            .minByOrNull { abs(sequence.expectedSecondsOf(it, tempo) - nowSeconds) }

        if (candidate != null) onsets[candidate] = nowSeconds
    }

    private fun finish(
        sequence: ToneSequence,
        tempo: Tempo,
        notesReached: Int,
        onsets: Map<Int, Double>,
        checkedPitch: Boolean,
        checkedTiming: Boolean,
    ) {
        val reached = notesReached.coerceAtMost(sequence.steps.size)
        val attempts = (0 until reached).mapNotNull { index ->
            val note = sequence.steps[index].note ?: return@mapNotNull null
            NoteAttempt(
                index = index,
                note = note,
                pitchCorrect = if (checkedPitch) index in onsets else null,
                timing = if (checkedTiming && checkedPitch) {
                    config.timing.judge(sequence.expectedSecondsOf(index, tempo), onsets[index])
                } else {
                    null
                },
            )
        }

        _uiState.value = _uiState.value.copy(
            phase = SequencePhase.Finished(
                SequenceResult(
                    sequenceName = sequence.name.orEmpty(),
                    attempts = attempts,
                    plannedNoteCount = sequence.steps.count { !it.isRest },
                    completed = reached >= sequence.steps.size,
                    checkedPitch = checkedPitch,
                    checkedTiming = checkedTiming && checkedPitch,
                )
            ),
            progress = null,
        )
    }

    private fun publishProgress(
        runner: SequenceRunner,
        sequence: ToneSequence,
        elapsed: Beats,
        detected: DetectedNote?,
        onsets: Map<Int, Double>,
    ) {
        val state = _uiState.value
        // Null while the sequence is resting, which is what the screen says and what nothing
        // grades.
        val current = runner.currentStep.note
        val written = detected?.let { state.writtenNoteOf(it.note) }
        val perPass = state.notesPerPass.coerceAtLeast(1)

        _uiState.value = state.copy(
            progress = SequenceProgress(
                noteIndex = runner.currentIndex,
                noteCount = sequence.steps.size,
                passIndex = runner.currentIndex / perPass,
                passCount = state.repeats,
                currentNote = current,
                elapsedBeats = elapsed,
                totalBeats = sequence.totalBeats,
                noteStartBeats = sequence.startBeatOf(runner.currentIndex),
                noteEndBeats = sequence.endBeatOf(runner.currentIndex),
                detectedNote = written,
                isCorrect = current != null && written == current,
                playedCorrectly = onsets.keys.toSet(),
            ),
        )
    }

    private fun updateSetup(change: (SequenceUiState) -> SequenceUiState) {
        if (_uiState.value.isRunning) return
        val changed = change(_uiState.value)
        _uiState.value = changed
        rememberSetup(changed)
    }

    /**
     * Writes the setup down, as it is changed rather than only when a run begins.
     *
     * The setup screen is left by going back, which throws this ViewModel away: anything not
     * written down by then is gone. Remembering only at Start therefore lost every choice made by
     * a player who set the exercise up and then thought better of starting it.
     *
     * A ready-made exercise is somebody else's decision and is still never written down as the
     * player's own. One handed over to be edited is the player's again, and is.
     */
    private fun rememberSetup(state: SequenceUiState) {
        if (state.isPreconfigured) return
        exerciseSetups.remember(Exercise.SEQUENCE, state.toSetup())
    }

    /** Overlays a ready-made exercise. Only this run is configured; nothing is written to settings. */
    private fun SequenceUiState.withPreset(preset: SequencePreset): SequenceUiState {
        val definition = library.firstOrNull { it.id == preset.sequenceId } ?: selected
        return copy(
            selectedId = definition?.id ?: selectedId,
            tempo = preset.tempo,
            addInverse = preset.addInverse,
            repeats = preset.repeats.coerceIn(repeatRange),
            metronome = preset.metronome && metronomeAvailable,
            checkPitch = preset.checkPitch && audioCapture != null,
            checkTiming = preset.checkTiming,
            isPreconfigured = !preset.editable,
        ).withMaterial()
    }

    /** Rebuilds the notes to practise from the sequence and the shape asked of it. */
    private fun SequenceUiState.withMaterial(): SequenceUiState {
        val definition = selected ?: return this
        return copy(sequence = definition.build(addInverse = addInverse, repeats = repeats))
    }

    private fun initialState(): SequenceUiState {
        val settings = settingsRepository.settings.value
        val definition = config.library.firstOrNull { it.id == config.defaultSequenceId }
            ?: config.library.first()

        val state = SequenceUiState(
            library = config.library,
            selectedId = definition.id,
            addInverse = config.defaultAddInverse,
            repeats = config.defaultRepeats,
            repeatRange = config.repeatRange,
            sequence = definition.build(
                addInverse = config.defaultAddInverse,
                repeats = config.defaultRepeats,
            ),
            tempo = definition.suggestedTempo,
            metronomeAvailable = metronome != null,
            metronome = metronome != null,
            transposition = settings.instrument.transposition,
            instrumentName = settings.instrument.displayName,
            noteNaming = settings.noteNaming,
        )
        // A preset decides everything the setup screen would have asked, so a remembered setup has
        // nothing left to say about it.
        return if (preset != null) {
            state.withPreset(preset)
        } else {
            state.withSetup(
                setup = exerciseSetups.setupOf(Exercise.SEQUENCE),
                canCheck = audioCapture != null,
            ).withMaterial()
        }
    }

    private companion object {
        /** Marker that ends the tick collection; the value itself carries no meaning. */
        val EXERCISE_OVER = Any()
    }
}

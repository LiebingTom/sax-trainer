package com.example.saxtrainer.feature.longtones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.clickCountIn
import com.example.saxtrainer.core.exercise.BeatClock
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.PauseSwitch
import com.example.saxtrainer.core.exercise.SequenceRunner
import com.example.saxtrainer.core.exercise.countIn
import com.example.saxtrainer.core.exercise.leadIn
import com.example.saxtrainer.core.exercise.leadInBeatsFor
import com.example.saxtrainer.core.exercise.pausedBy
import com.example.saxtrainer.core.fingering.NotePickerState
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.matchingSet
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import com.example.saxtrainer.core.settings.displayAt
import com.example.saxtrainer.core.settings.noteDisplaySchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.saxtrainer.core.music.soundedNote

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
    /**
     * What this exercise was last started with. Defaulted so an exercise without one still runs; it
     * simply opens on the plain defaults every time.
     */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    /**
     * A ready-made exercise, where somebody else decided what to practise.
     *
     * Overrides the remembered setup, since a preset decides everything the setup screen would have
     * asked. Null is the ordinary case: the player sets the exercise up themselves.
     */
    private val preset: LongTonePreset? = null,
    /** Only ever used to click the count-in; the exercise itself is played into a silent room. */
    private val metronome: Metronome? = null,
    private val config: LongToneConfig = LongToneConfig(),
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Only ever drawn from when the presentation of a note is left to chance. */
    private val random: Random = Random.Default,
    /** Only ever used for the count-in; the exercise itself runs on audio time. */
    private val countInClock: BeatClock = MonotonicBeatClock(),
) : ViewModel() {

    /** How an attempt ended. Every exit path produces exactly one of these. */
    private sealed interface Outcome {
        data object Completed : Outcome

        data object StoppedEarly : Outcome

        data class Failed(val error: AudioCaptureError) : Outcome
    }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<LongToneUiState> = _uiState.asStateFlow()

    private var autoStartPending = preset?.autoStart == true

    private var exerciseJob: Job? = null

    /** Held for the whole life of the ViewModel; a new attempt starts from it released. */
    private val pause = PauseSwitch()

    init {
        // Instrument only affects how notes are labelled, so the exercise itself is unaffected.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                    noteNaming = settings.noteNaming,
                    octaveNumbering = settings.octaveNumbering,
                    noteDisplay = settings.noteDisplayFor(TrainingTool.LONG_TONES),
                    countInClick = settings.countInClick,
                )
            }
        }
    }

    /** Opens the note window on the notes the next run would hold. */
    fun openNotePicker() = updateSetup {
        val chosen = it.selection.writtenNotes.toSet()
        it.copy(notePicker = NotePickerState.open(it.availableNotes, chosen))
    }

    /** One change to the open window's draft; nothing is decided until [applyNotePicker]. */
    fun updateNotePicker(change: (NotePickerState) -> NotePickerState) = updateSetup { state ->
        val picker = state.notePicker ?: return@updateSetup state
        state.copy(notePicker = change(picker))
    }

    fun startNotePickerFrom(set: NoteSet) = updateNotePicker { it.startFrom(set, config.chart) }

    /**
     * Takes the draft. Exactly a ready-made set is stored as that set; anything else is the
     * player's own, and becomes the custom set.
     */
    fun applyNotePicker() = updateSetup { state ->
        val picker = state.notePicker?.takeIf { it.canApply } ?: return@updateSetup state
        val set = NoteSets.matchingSet(picker.chosen, config.chart)
        val customNotes = if (set == NoteSet.CUSTOM) picker.chosen else state.customNotes
        state.copy(
            noteSet = set,
            customNotes = customNotes,
            notePicker = null,
            selection = state.selection.copy(
                writtenNotes = NoteSets.notesOf(set, config.chart, customNotes),
            ),
        )
    }

    fun closeNotePicker() = updateSetup { it.copy(notePicker = null) }

    /** Changes how this tool shows notes; the shared default is left alone. */
    fun selectNoteDisplay(noteDisplay: NoteDisplay) {
        settingsRepository.setNoteDisplayFor(TrainingTool.LONG_TONES, noteDisplay)
    }

    fun selectBeatsPerNote(beats: Beats) = updateSelection { it.copy(beatsPerNote = beats) }

    fun setShowNextNote(show: Boolean) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(showNextNote = show)
        rememberSetup()
    }

    /** Starts the count-in and recording. The caller must have obtained microphone permission. */
    fun start() {
        if (exerciseJob?.isActive == true) return

        // Already written down as it was chosen; this is the setup as it is actually started.
        rememberSetup()

        pause.resume()
        _uiState.value = _uiState.value.copy(
            phase = LongTonePhase.Countdown(countInBeats()),
            progress = null,
            measurements = LongToneMeasurements(),
            isPaused = false,
        )
        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise() }
    }

    /**
     * Holds the attempt, or lets it go again.
     *
     * Not the same as [stop]: the microphone stays open but nothing it hears is analysed, so the
     * note keeps its place and no result is published. Above all this is what makes a long
     * sequence of tones practicable - it can be broken off in the middle and picked up again.
     */
    fun togglePause() {
        if (!_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isPaused = pause.toggle())
    }

    /**
     * Ends the exercise early and keeps what was achieved. Cancelling the job also cancels the
     * capture flow, which releases the recorder in its own `finally` block.
     */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        pause.resume()
        _uiState.value = _uiState.value.copy(isPaused = false)
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
        metronome?.release()
        super.onCleared()
    }

    private suspend fun runExercise() {
        val selection = _uiState.value.selection
        // Each note held for the same length, one after another - what is listened for is the pitch
        // each written note sounds as on the instrument.
        val sequence = ToneSequence(
            steps = _uiState.value.concertNotes.map { ToneStep(it, selection.beatsPerNote) },
            timeSignature = TimeSignature.FOUR_FOUR,
        )
        val runner = SequenceRunner(sequence)
        // Decided once for the whole exercise so a note cannot change its presentation while it is
        // being held, and so the preview of the next note is honest.
        val displays = noteDisplaySchedule(
            display = _uiState.value.noteDisplay,
            stepCount = sequence.steps.size,
            random = random,
        )
        val processor = LongToneProcessor(config, config.capture.sampleRate)
        val results = mutableListOf<LongToneStepResult>()

        try {
            countIn()
            processor.startStep(runner.currentStep.soundedNote.frequencyHz())
            _uiState.value = _uiState.value.copy(
                phase = LongTonePhase.Running,
                progress = progressOf(runner, displays, selection, stepElapsed = 0f, exerciseElapsed = 0f),
            )

            val outcome = collectFrames(runner, displays, processor, selection, results)
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

    /**
     * Counts the bar in at the exercise's own tempo, using the count-in every practice shares.
     *
     * This exercise brings its own length - a bar - so it does not read the count-in setting; a
     * practice with nothing of its own to say counts off what the settings ask for instead.
     */
    private suspend fun countIn() {
        val state = _uiState.value
        val tempo = state.selection.tempo
        val beats = countInBeats()
        val held = countInClock.pausedBy(pause)
        // start() has already put the count on screen at its starting number, so the quiet lead-in
        // shows it standing there.
        held.leadIn(leadInBeatsFor(beats), tempo)

        // The click stops before the microphone opens, so it can never end up in the recording.
        val engine = metronome?.takeIf { state.countInClick } ?: run {
            held.countIn(beats, tempo, ::publishCountdown)
            return
        }

        // A clicked count cannot be held mid-stream: pausing stops the click and continuing counts
        // again from the top, which is what an interrupted count-in is worth anyway.
        var counted = false
        while (!counted) {
            pause.isPaused.first { !it }
            counted = coroutineScope {
                val counting = launch { engine.clickCountIn(beats, tempo, ::publishCountdown) }
                val watcher = launch {
                    pause.isPaused.first { it }
                    counting.cancel()
                }
                counting.join()
                watcher.cancel()
                !pause.paused
            }
            engine.stop()
        }
    }

    private fun publishCountdown(beatsRemaining: Int) {
        _uiState.value = _uiState.value.copy(phase = LongTonePhase.Countdown(beatsRemaining))
    }

    /**
     * Collects capture frames until the exercise ends, then stops collecting - which ends the
     * capture flow and releases the microphone. The coroutine never cancels itself.
     */
    private suspend fun collectFrames(
        runner: SequenceRunner,
        displays: List<NoteDisplay>,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
        results: MutableList<LongToneStepResult>,
    ): Outcome {
        var lastUiUpdateSeconds = -config.uiUpdateIntervalMs / MILLIS_PER_SECOND

        return audioCapture.frames(config.capture)
            .mapNotNull { event ->
                when (event) {
                    is AudioCaptureEvent.Failed -> Outcome.Failed(event.error)

                    is AudioCaptureEvent.Frame -> if (pause.paused) {
                        // Frames still arrive - the microphone is never handed back mid-attempt -
                        // but nothing is analysed, so audio time genuinely stands still.
                        null
                    } else {
                        val frame = event.frame
                        processor.processFrame(frame.samples, frame.newSamples)

                        if (noteFinished(processor, runner, selection)) {
                            results += processor.stepResult(runner.currentStep, completed = true)
                            if (runner.advance()) {
                                processor.startStep(runner.currentStep.soundedNote.frequencyHz())
                                lastUiUpdateSeconds = -config.uiUpdateIntervalMs / MILLIS_PER_SECOND
                                publishProgress(runner, displays, processor, selection)
                                null
                            } else {
                                Outcome.Completed
                            }
                        } else {
                            val elapsed = processor.analyzedSeconds
                            val interval = config.uiUpdateIntervalMs / MILLIS_PER_SECOND
                            if (elapsed - lastUiUpdateSeconds >= interval) {
                                lastUiUpdateSeconds = elapsed
                                publishProgress(runner, displays, processor, selection)
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
        displays: List<NoteDisplay>,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
    ) {
        _uiState.value = _uiState.value.copy(
            progress = progressOf(
                runner = runner,
                displays = displays,
                selection = selection,
                stepElapsed = processor.analyzedSeconds,
                exerciseElapsed = processor.exerciseSeconds,
            ),
            measurements = processor.measurements(),
        )
    }

    private fun progressOf(
        runner: SequenceRunner,
        displays: List<NoteDisplay>,
        selection: ExerciseSelection,
        stepElapsed: Float,
        exerciseElapsed: Float,
    ): ExerciseProgress {
        val sequenceProgress = runner.progress
        return ExerciseProgress(
            noteIndex = sequenceProgress.stepIndex,
            noteCount = sequenceProgress.stepCount,
            currentNote = runner.currentStep.soundedNote,
            upcomingNotes = runner.upcoming(UPCOMING_NOTES).map { it.soundedNote },
            noteElapsedSeconds = stepElapsed,
            noteTargetSeconds = selection.tempo.secondsFor(runner.currentStep.duration).toFloat(),
            exerciseElapsedSeconds = exerciseElapsed,
            exerciseTotalSeconds = selection.totalSeconds,
            noteDisplay = displays.displayAt(sequenceProgress.stepIndex),
            nextNoteDisplay = displays.displayAt(sequenceProgress.stepIndex + 1),
        )
    }

    private fun publish(
        outcome: Outcome,
        processor: LongToneProcessor,
        selection: ExerciseSelection,
        results: List<LongToneStepResult>,
    ) {
        pause.resume()
        val state = _uiState.value.copy(isPaused = false)
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

    private fun updateSelection(change: (ExerciseSelection) -> ExerciseSelection) =
        updateSetup { it.copy(selection = change(it.selection)) }

    /** A change to the setup: refused while the exercise runs, written down as soon as it is made. */
    private fun updateSetup(change: (LongToneUiState) -> LongToneUiState) {
        if (_uiState.value.isBusy) return
        _uiState.value = change(_uiState.value)
        rememberSetup()
    }

    /**
     * Writes the setup down, as it is changed rather than only when a run begins.
     *
     * The setup screen is left by going back, which throws this ViewModel away: anything not
     * written down by then is gone. Remembering only at Start therefore lost every choice made by
     * a player who set the exercise up and then thought better of starting it.
     */
    private fun rememberSetup() {
        // A ready-made exercise is somebody else's decision and is never written down as the
        // player's own. One handed over to be edited is the player's again, and is.
        if (_uiState.value.isPreconfigured) return
        exerciseSetups.remember(Exercise.LONG_TONES, _uiState.value.toSetup())
    }

    /**
     * Whether the screen should start the exercise without being asked, answered true only once.
     *
     * The screen owns the microphone permission flow, so the decision to start is taken there
     * rather than here, where the exercise would begin deaf.
     */
    /**
     * Writes the current setup down now, without waiting for it to be changed or started.
     *
     * What the setup screen is showing is a practice whether or not the player has touched a
     * control, so anything outside that wants to keep it - "add to your lessons" - has to be able
     * to ask for it. Nothing else about when a setup is remembered changes: the untouched defaults
     * are only written when somebody actually asks for them.
     */
    fun rememberSetupNow() = rememberSetup()

    fun consumeAutoStart(): Boolean {
        if (!autoStartPending) return false
        autoStartPending = false
        return true
    }

    /**
     * Overlays a ready-made exercise. Only the run is configured: nothing is written back to the
     * player's settings, and the setup screen is skipped rather than pre-filled unless the preset
     * asks to be edited.
     *
     * What this build no longer offers is left as it was, exactly as a remembered setup is: a note
     * the chart cannot finger, a hold that is not one of the choices. A preset handed over to be
     * edited fills the setup screen in, so its notes become the set they are - or the player's own.
     */
    private fun LongToneUiState.withPreset(preset: LongTonePreset): LongToneUiState {
        val notes = preset.notes
            .filter { config.chart.supports(it.note) }
            .ifEmpty { selection.writtenNotes }
        val set = if (preset.editable) NoteSets.matchingSet(notes, config.chart) else noteSet
        return copy(
            noteSet = set,
            customNotes = if (preset.editable && set == NoteSet.CUSTOM) notes.toSet() else customNotes,
            showNextNote = preset.showNextNote,
            isPreconfigured = !preset.editable,
            selection = selection.copy(
                writtenNotes = notes,
                beatsPerNote = preset.beatsPerNote.takeIf { it in beatOptions }
                    ?: selection.beatsPerNote,
            ),
        )
    }

    /** One bar of count-in, in beats. */
    private fun countInBeats(): Int = config.countInBars * COUNT_IN_BEATS_PER_BAR

    private fun initialState(): LongToneUiState {
        val instrument = settingsRepository.settings.value.instrument
        val everyPitch = NoteSets.notesOf(NoteSet.CHROMATIC, config.chart, emptySet()).toSet()
        val state = LongToneUiState(
            selection = ExerciseSelection(
                writtenNotes = NoteSets.notesOf(NoteSet.BEGINNER, config.chart, everyPitch),
                beatsPerNote = config.defaultBeatsPerNote,
                tempo = config.defaultTempo,
            ),
            availableNotes = NoteSets.choicesOf(config.chart),
            noteSet = NoteSet.BEGINNER,
            customNotes = everyPitch,
            beatOptions = config.beatChoices,
            transposition = instrument.transposition,
            instrumentName = instrument.displayName,
            noteDisplay = settingsRepository.settings.value.noteDisplayFor(TrainingTool.LONG_TONES),
            countInClick = settingsRepository.settings.value.countInClick,
            centsDisplayRange = config.centsDisplayRange,
        )
        // A preset decides everything the setup screen would have asked, so a remembered setup has
        // nothing left to say about it.
        return if (preset != null) {
            state.withPreset(preset)
        } else {
            state.withSetup(exerciseSetups.setupOf(Exercise.LONG_TONES), config.chart)
        }
    }

    private companion object {
        const val COUNT_IN_BEATS_PER_BAR = 4
        const val MILLIS_PER_SECOND = 1_000f
        const val UPCOMING_NOTES = 1
    }
}

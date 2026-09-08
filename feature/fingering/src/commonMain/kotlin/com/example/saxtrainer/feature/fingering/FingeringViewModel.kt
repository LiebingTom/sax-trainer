package com.example.saxtrainer.feature.fingering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.listening.Hearing
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.clickCountIn
import com.example.saxtrainer.core.exercise.BeatClock
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.exercise.PauseSwitch
import com.example.saxtrainer.core.exercise.SequenceRunner
import com.example.saxtrainer.core.exercise.countIn
import com.example.saxtrainer.core.exercise.discountingPausedSeconds
import com.example.saxtrainer.core.exercise.leadIn
import com.example.saxtrainer.core.exercise.leadInBeatsFor
import com.example.saxtrainer.core.exercise.pausedBy
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import com.example.saxtrainer.core.settings.displayAt
import com.example.saxtrainer.core.settings.noteDisplaySchedule
import kotlin.concurrent.Volatile
import kotlin.random.Random
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
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/**
 * Runs a fingering exercise: show a note with its fingering, hold it for the chosen time, move on.
 *
 * With checking off it is a chart to read: note and fingering side by side, held for the chosen
 * time. With checking on it becomes the exercise proper - the note is shown, the fingering is not,
 * and the player has to find the grip and sound it. The fingering then appears as the answer,
 * whether it was earned or the time simply ran out, because being shown the grip you could not
 * remember is the point of a fingering trainer.
 *
 * A wrong note is said out loud and changes nothing else: the note stays until it is played or its
 * time is up. A sound too unclear to read a pitch from is not a wrong note and is never counted as
 * one. How quickly any of it happened is not measured here - that is the note trainer's exercise.
 *
 * Randomisation happens once, when the exercise is generated; [SequenceRunner] then walks a fixed
 * sequence, which keeps it a passive cursor and makes an exercise reproducible from a seed.
 *
 * A [preset] hands the trainer a ready-made exercise instead of asking the player to build one. The
 * trainer neither knows nor cares where it came from.
 *
 * The trainer has no tempo of its own, so the count-in it shares with every other practice is
 * counted at [FingeringConfig.tempo] - the same reference speed the exercise itself is built at. A
 * [metronome] counts it out loud; without one, or with the click switched off, the same count runs
 * silently on the clock.
 */
class FingeringViewModel(
    private val settingsRepository: SettingsRepository,
    /**
     * What this trainer was last started with. Defaulted so a trainer without one still runs; it
     * simply opens on the plain defaults every time.
     */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val audioCapture: AudioCapture? = null,
    /** Only ever used to click the count-in; this trainer has no click of its own to keep. */
    private val metronome: Metronome? = null,
    private val preset: FingeringPreset? = null,
    private val config: FingeringConfig = FingeringConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: FingeringSessionClock = FingeringSessionClock(config.tickIntervalMs),
    private val countInClock: BeatClock = MonotonicBeatClock(config.tickIntervalMs),
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<FingeringUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null

    /** Held for the whole life of the ViewModel; a new run starts from it released. */
    private val pause = PauseSwitch()

    /** A preset that asked to start does so once, not again on every recomposition or rotation. */
    private var autoStartPending = preset?.autoStart == true

    /** Set by [skipSuccessDelay] and cleared as soon as the note it belongs to is over. */
    @Volatile
    private var skipRequested = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                    noteNaming = settings.noteNaming,
                    countInBeats = settings.countInBeats,
                    countInClick = settings.countInClick,
                    // A preset fixes the presentation for its own run; the player's saved
                    // preference is neither read nor written while it does.
                    noteDisplay = preset?.noteDisplay
                        ?: settings.noteDisplayFor(TrainingTool.FINGERING),
                )
            }
        }
    }

    fun selectNoteSet(noteSet: NoteSet) = updateSetup { it.withNoteSet(noteSet) }

    /** Opens the custom notes dialog on a copy of the stored selection. */
    fun editCustomNotes() = updateSetup { it.copy(customNotesDraft = it.customNotes) }

    fun toggleCustomNote(note: Note) = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotesDraft = if (note in draft) draft - note else draft + note)
    }

    fun selectAllCustomNotes() = updateSetup { it.copy(customNotesDraft = it.availableNotes.toSet()) }

    fun clearCustomNotes() = updateSetup { it.copy(customNotesDraft = emptySet()) }

    /** Keeps the edited selection and switches to it. */
    fun applyCustomNotes() = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotes = draft, customNotesDraft = null).withNoteSet(NoteSet.CUSTOM)
    }

    /** Throws the edit away; the stored selection is untouched. */
    fun cancelCustomNotes() = updateSetup { it.copy(customNotesDraft = null) }

    fun setRandomOrder(random: Boolean) = updateSetup {
        it.copy(order = if (random) NoteOrder.RANDOM else NoteOrder.ORDERED)
    }

    fun setBeatsPerNote(beats: Beats) = updateSetup { it.copy(beatsPerNote = beats) }

    fun setTempo(bpm: Int) = updateSetup { it.copy(tempo = Tempo(bpm.coerceIn(it.tempoRange))) }

    fun setShowNextNote(show: Boolean) = updateSetup { it.copy(showNextNote = show) }

    fun setCheckPlaying(enabled: Boolean) = updateSetup {
        it.copy(checkPlaying = enabled, checkUnavailable = false)
    }

    fun setSuccessDelaySeconds(seconds: Float) = updateSetup {
        it.copy(successDelaySeconds = seconds.coerceAtLeast(0f))
    }

    /**
     * Cuts the wait after a note played right short and goes straight to the next one.
     *
     * Only heard while the trainer is waiting, so a tap can never stand in for playing a note: what
     * ends a note that is still being asked for is the microphone or the clock, never the screen.
     */
    fun skipSuccessDelay() {
        if (_uiState.value.progress?.landed == true) skipRequested = true
    }

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

    /**
     * Whether the screen should start the exercise without being asked, answered true only once.
     *
     * The screen owns the microphone permission flow, so the decision to start is taken there
     * rather than here, where a listening exercise would begin deaf.
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

    fun start() {
        if (exerciseJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canStart) return

        // Already written down as it was chosen; this is the setup as it is actually
        // started.
        rememberSetup(state)

        val sequence = FingeringExerciseGenerator.generate(
            notes = state.activeNotes,
            order = state.order,
            beatsPerNote = state.beatsPerNote,
            rounds = state.rounds,
            random = random,
        )

        pause.resume()
        skipRequested = false
        _uiState.value = _uiState.value.copy(isPaused = false)
        exerciseJob = viewModelScope.launch(processingDispatcher) {
            runExercise(SequenceRunner(sequence))
        }
    }

    /**
     * Holds the run, or lets it go again.
     *
     * Distinct from [stop]: the exercise keeps its place and its notes, and nothing is reported. A
     * count-in is held too, so continuing gives the player the rest of their count rather than
     * dropping them straight onto the first note.
     */
    fun togglePause() {
        if (!_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isPaused = pause.toggle())
    }

    /** Ends the exercise early and keeps the notes that were practised. */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        pause.resume()
        // A count-in has practised nothing, so there is no result to publish - and cancelling it may
        // happen before the exercise coroutine runs again, in which case nothing else would move the
        // state on.
        _uiState.value = _uiState.value.copy(isPaused = false)
        if (_uiState.value.phase is FingeringPhase.CountIn) {
            _uiState.value = _uiState.value.copy(phase = FingeringPhase.Idle)
        }
        job?.cancel()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(phase = FingeringPhase.Idle, progress = null)
    }

    override fun onCleared() {
        stop()
        metronome?.release()
        super.onCleared()
    }

    private suspend fun runExercise(runner: SequenceRunner) = coroutineScope {
        val practised = mutableListOf<PractisedNote>()
        val noteCount = runner.sequence.steps.size
        // Decided once for the whole exercise so a note cannot change its presentation while it is
        // being read, and so the preview of the next note is honest.
        val displays = noteDisplaySchedule(_uiState.value.noteDisplay, noteCount, random)
        val listening = _uiState.value.checkPlaying && audioCapture != null
        // Fixed for the run, like everything else the setup screen decided.
        val successDelaySeconds = _uiState.value.successDelaySeconds
        val heard = MutableStateFlow<Hearing>(Hearing.Silent)
        var listenJob: Job? = null

        var noteStartSeconds = 0f
        // When the fingering went up, in this note's own time. Null while it is still being asked
        // for; once set, the note is over as soon as the answer has been on screen long enough.
        var revealedSeconds: Float? = null
        var correctSinceSeconds: Float? = null
        var playedCorrectly = false

        try {
            countIn()

            // The microphone is opened after the count, so a click can never be heard as a note.
            // Listening only reports; the clock still runs the exercise.
            if (listening) {
                listenJob = launch {
                    NoteListener(audioCapture, config.capture, config.listening)
                        .listen()
                        .collect { heard.value = it }
                }
            }

            publishProgress(runner, displays, elapsedInNote = 0f, listening = listening)
            _uiState.value = _uiState.value.copy(phase = FingeringPhase.Running)

            // Collection stops once the last note is over; the coroutine never cancels itself.
            clock.elapsedSeconds()
                .discountingPausedSeconds(pause)
                .mapNotNull { elapsed ->
                    val noteSeconds = noteSecondsOf(runner)
                    val elapsedInNote = elapsed - noteStartSeconds
                    val written = runner.currentStep.note

                    val hearing = if (listening) heard.value else Hearing.Silent
                    val state = _uiState.value
                    val sounding = (hearing as? Hearing.Pitched)?.detected?.note
                    val correct = sounding != null && sounding == state.soundingNoteOf(written)

                    when {
                        correct -> if (correctSinceSeconds == null) {
                            correctSinceSeconds = elapsedInNote
                        }
                        // An unclear sound says nothing either way, so it neither counts against
                        // the player nor breaks a note they are already holding.
                        hearing is Hearing.Unclear -> Unit
                        else -> correctSinceSeconds = null
                    }

                    val heldSeconds = correctSinceSeconds?.let { elapsedInNote - it } ?: 0f
                    if (revealedSeconds == null &&
                        listening &&
                        heldSeconds >= config.requiredCorrectSeconds
                    ) {
                        playedCorrectly = true
                        revealedSeconds = elapsedInNote
                    }
                    // Time up without it being played: the fingering is shown anyway. Not knowing a
                    // grip is exactly when a player needs to be shown it.
                    if (revealedSeconds == null && listening && elapsedInNote >= noteSeconds) {
                        revealedSeconds = elapsedInNote
                    }

                    val over = if (listening) {
                        revealedSeconds?.let { shownSince ->
                            val shownFor = elapsedInNote - shownSince
                            if (playedCorrectly) {
                                // The player got it, so the wait is theirs: it lasts as long as they
                                // asked for, or until they tap the panel to go on.
                                skipRequested || shownFor >= successDelaySeconds
                            } else {
                                shownFor >= config.revealSeconds
                            }
                        } == true
                    } else {
                        elapsedInNote >= noteSeconds
                    }

                    if (!over) {
                        publishProgress(
                            runner = runner,
                            displays = displays,
                            elapsedInNote = elapsedInNote,
                            listening = listening,
                            revealed = revealedSeconds != null,
                            hearing = hearing,
                            correct = correct,
                            landed = playedCorrectly,
                        )
                        null
                    } else {
                        practised += PractisedNote(written, playedCorrectly)
                        // The next note starts where this one ended, however long it actually took.
                        noteStartSeconds = elapsed
                        revealedSeconds = null
                        correctSinceSeconds = null
                        playedCorrectly = false
                        skipRequested = false
                        if (runner.advance()) {
                            publishProgress(runner, displays, elapsedInNote = 0f, listening = listening)
                            null
                        } else {
                            EXERCISE_OVER
                        }
                    }
                }
                .firstOrNull()

            finish(practised, noteCount, listening)
        } catch (cancellation: CancellationException) {
            // Stopped by the player or by leaving the screen: report what was practised. A run
            // that never got past its count-in has nothing to report; stop() has already said so.
            if (_uiState.value.phase is FingeringPhase.Running) {
                finish(practised, noteCount, listening)
            }
            throw cancellation
        } finally {
            listenJob?.cancel()
        }
    }

    /**
     * Counts the player in, out loud when there is a metronome and the click is switched on.
     *
     * A clicked count cannot simply be held: the engine is a stream, not a position, so pausing
     * stops the click and continuing counts again from the top - which is what a count-in
     * interrupted half way is worth anyway. A silent count is a clock and is simply held.
     */
    private suspend fun countIn() {
        val state = _uiState.value
        val beats = state.countInBeats
        val held = countInClock.pausedBy(pause)

        if (beats > 0) {
            // Shown standing at its starting number through the quiet lead-in, so the screen says
            // what is coming before the count begins to move.
            publishCountIn(beats)
            held.leadIn(leadInBeatsFor(beats), state.tempo)
        }

        val engine = metronome?.takeIf { state.countInClick } ?: run {
            held.countIn(beats, state.tempo, ::publishCountIn)
            return
        }

        var counted = false
        while (!counted) {
            pause.isPaused.first { !it }
            counted = coroutineScope {
                val counting = launch { engine.clickCountIn(beats, state.tempo, ::publishCountIn) }
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

    private fun publishCountIn(beatsRemaining: Int) {
        _uiState.value = _uiState.value.copy(phase = FingeringPhase.CountIn(beatsRemaining))
    }

    private fun finish(practised: List<PractisedNote>, plannedNoteCount: Int, checked: Boolean) {
        pause.resume()
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            phase = FingeringPhase.Finished(
                FingeringResult(
                    notes = practised.toList(),
                    plannedNoteCount = plannedNoteCount,
                    checked = checked,
                ),
            ),
            progress = null,
        )
    }

    private fun publishProgress(
        runner: SequenceRunner,
        displays: List<NoteDisplay>,
        elapsedInNote: Float,
        listening: Boolean,
        revealed: Boolean = false,
        hearing: Hearing = Hearing.Silent,
        correct: Boolean = false,
        landed: Boolean = false,
    ) {
        val note = runner.currentStep.note
        val noteSeconds = noteSecondsOf(runner)
        val state = _uiState.value
        val sounding = (hearing as? Hearing.Pitched)?.detected?.note
        _uiState.value = state.copy(
            progress = FingeringProgress(
                noteIndex = runner.currentIndex,
                noteCount = runner.sequence.steps.size,
                currentNote = note,
                currentFingering = config.chart.fingeringFor(note),
                nextNote = runner.upcoming(1).firstOrNull()?.note,
                remainingSeconds = (noteSeconds - elapsedInNote).coerceAtLeast(0f),
                noteSeconds = noteSeconds,
                noteDisplay = displays.displayAt(runner.currentIndex),
                nextNoteDisplay = displays.displayAt(runner.currentIndex + 1),
                showFingering = !listening || revealed,
                heard = when {
                    !listening -> Heard.NOTHING
                    correct -> Heard.CORRECT
                    sounding != null -> Heard.WRONG
                    hearing is Hearing.Unclear -> Heard.UNCLEAR
                    else -> Heard.NOTHING
                },
                detectedNote = sounding?.let(state::writtenNoteOf),
                landed = landed,
            ),
        )
    }

    private fun noteSecondsOf(runner: SequenceRunner): Float =
        _uiState.value.tempo.secondsFor(runner.currentStep.duration).toFloat()

    private fun updateSetup(change: (FingeringUiState) -> FingeringUiState) {
        if (_uiState.value.isBusy) return
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
    private fun rememberSetup(state: FingeringUiState) {
        if (state.isPreconfigured) return
        exerciseSetups.remember(Exercise.FINGERING, state.toSetup())
    }

    /** Applies a note set and recomputes the pool the next exercise will use. */
    private fun FingeringUiState.withNoteSet(noteSet: NoteSet) = copy(
        noteSet = noteSet,
        activeNotes = NoteSets.notesOf(noteSet, config.chart, customNotes),
    )

    /**
     * Overlays a ready-made exercise. Only the run is configured: nothing is written back to the
     * player's settings.
     *
     * The setup screen is skipped rather than pre-filled, unless the preset asks to be edited - and
     * then the note set has to be filled in too, or the screen would claim a set it is not
     * practising: a preset names notes, so as something to edit it is always a custom set.
     */
    private fun FingeringUiState.withPreset(preset: FingeringPreset): FingeringUiState {
        val notes = preset.notes.filter(config.chart::supports)
        return copy(
            noteSet = if (preset.editable) NoteSet.CUSTOM else noteSet,
            customNotes = if (preset.editable) notes.toSet() else customNotes,
            activeNotes = notes,
            order = preset.order,
            beatsPerNote = preset.beatsPerNote,
            tempo = Tempo(preset.tempo.bpm.coerceIn(tempoRange)),
            showNextNote = preset.showNextNote,
            rounds = preset.rounds,
            checkPlaying = preset.checkPlaying && audioCapture != null,
            successDelaySeconds = preset.successDelaySeconds,
            noteDisplay = preset.noteDisplay ?: noteDisplay,
            isPreconfigured = !preset.editable,
        )
    }

    private fun initialState(): FingeringUiState {
        val notes = config.chart.supportedNotes
        val settings = settingsRepository.settings.value
        val state = FingeringUiState(
            availableNotes = notes,
            noteSet = NoteSet.BEGINNER,
            customNotes = notes.toSet(),
            activeNotes = NoteSets.notesOf(NoteSet.BEGINNER, config.chart, notes.toSet()),
            order = NoteOrder.RANDOM,
            beatsPerNote = Beats.of(config.defaultBeats),
            tempo = config.defaultTempo,
            tempoRange = config.tempoRange,
            rounds = config.defaultRounds,
            beatOptions = config.beatChoices,
            checkPlaying = config.checkPlayingByDefault && audioCapture != null,
            successDelaySeconds = config.defaultSuccessDelaySeconds,
            successDelayOptions = config.successDelayOptions,
            transposition = settings.instrument.transposition,
            instrumentName = settings.instrument.displayName,
            noteNaming = settings.noteNaming,
            noteDisplay = settings.noteDisplayFor(TrainingTool.FINGERING),
            countInBeats = settings.countInBeats,
            countInClick = settings.countInClick,
        )
        // A preset decides everything the setup screen would have asked, so a remembered setup has
        // nothing left to say about it.
        return if (preset != null) {
            state.withPreset(preset)
        } else {
            state.withSetup(
                setup = exerciseSetups.setupOf(Exercise.FINGERING),
                chart = config.chart,
                canCheck = audioCapture != null,
            )
        }
    }

    private companion object {
        /** Marker that ends the tick collection; the value itself carries no meaning. */
        val EXERCISE_OVER = Any()
    }
}

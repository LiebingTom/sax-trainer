package com.example.saxtrainer.feature.notetrainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.listening.Hearing
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.clickCountIn
import com.example.saxtrainer.core.exercise.BeatClock
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.monotonicNanos
import kotlin.concurrent.Volatile
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.exercise.PauseSwitch
import com.example.saxtrainer.core.exercise.countIn
import com.example.saxtrainer.core.exercise.leadIn
import com.example.saxtrainer.core.exercise.leadInBeatsFor
import com.example.saxtrainer.core.exercise.pausedBy
import com.example.saxtrainer.core.exercise.practiceOrder
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * Runs a note trainer exercise: read the note, find the fingering, play it, next.
 *
 * Driven by what the microphone hears rather than by a clock - the one architectural difference
 * from the fingering and sequence trainers, and the reason it can measure a reaction at all. A note
 * waits as long as the player needs; nothing times out and nothing fails. What is measured is how
 * long the note took and how many other notes were tried on the way, and [skip] is there so a note
 * nobody can find never traps the session.
 *
 * A wrong note is reported and changes nothing else. A sound with no pitch in it - a breath, a
 * squeak, an unsettled attack - is neither right nor wrong and is never counted as a wrong try.
 *
 * The count-in is the one every practice shares, counted at [NoteTrainerConfig.tempo] because this
 * trainer has no tempo of its own. It matters here for a second reason: the microphone is opened
 * after it, so a click can never be heard as a played note.
 */
class NoteTrainerViewModel(
    private val settingsRepository: SettingsRepository,
    private val audioCapture: AudioCapture? = null,
    /** Only ever used to click the count-in; this trainer has no click of its own to keep. */
    private val metronome: Metronome? = null,
    private val preset: NoteTrainerPreset? = null,
    private val config: NoteTrainerConfig = NoteTrainerConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val countInClock: BeatClock = MonotonicBeatClock(),
    /** Injectable so tests can measure a reaction on virtual time. */
    private val nanoTime: () -> Long = monotonicNanos,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<NoteTrainerUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null

    /** Held for the whole life of the ViewModel; a new run starts from it released. */
    private val pause = PauseSwitch()

    /** Set by [skip] and cleared as the next note is presented. */
    @Volatile
    private var skipRequested = false

    /** A preset that asked to start does so once, not again on every recomposition or rotation. */
    private var autoStartPending = preset?.autoStart == true

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    transposition = settings.instrument.transposition,
                    instrumentName = settings.instrument.displayName,
                    noteNaming = settings.noteNaming,
                    countInBeats = settings.countInBeats,
                    countInClick = settings.countInClick,
                )
            }
        }
    }

    fun selectNoteSet(noteSet: NoteSet) = updateSetup { it.withNoteSet(noteSet) }

    fun editCustomNotes() = updateSetup { it.copy(customNotesDraft = it.customNotes) }

    fun toggleCustomNote(note: Note) = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotesDraft = if (note in draft) draft - note else draft + note)
    }

    fun selectAllCustomNotes() = updateSetup { it.copy(customNotesDraft = it.availableNotes.toSet()) }

    fun clearCustomNotes() = updateSetup { it.copy(customNotesDraft = emptySet()) }

    fun applyCustomNotes() = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotes = draft, customNotesDraft = null).withNoteSet(NoteSet.CUSTOM)
    }

    fun cancelCustomNotes() = updateSetup { it.copy(customNotesDraft = null) }

    fun setRandomOrder(random: Boolean) = updateSetup {
        it.copy(order = if (random) NoteOrder.RANDOM else NoteOrder.ORDERED)
    }

    fun setRounds(rounds: Int) = updateSetup { it.copy(rounds = rounds.coerceAtLeast(1)) }

    fun setShowFingeringHint(show: Boolean) = updateSetup { it.copy(showFingeringHint = show) }

    fun setShowNoteName(show: Boolean) = updateSetup { it.copy(showNoteName = show) }

    fun setTargetSeconds(seconds: Int) = updateSetup { it.copy(targetSeconds = seconds) }

    /** Called by the screen when the microphone was asked for and refused. */
    fun onMicrophoneUnavailable() {
        _uiState.value = _uiState.value.copy(microphoneUnavailable = true)
    }

    /**
     * Whether the screen should start the exercise without being asked, answered true only once.
     *
     * The screen owns the microphone permission flow, so the decision to start is taken there
     * rather than here, where the exercise would begin deaf.
     */
    fun consumeAutoStart(): Boolean {
        if (!autoStartPending) return false
        autoStartPending = false
        return true
    }

    fun start() {
        if (exerciseJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canStart || audioCapture == null) return

        val notes = practiceOrder(state.activeNotes, state.order, state.rounds, random)

        pause.resume()
        skipRequested = false
        _uiState.value = _uiState.value.copy(isPaused = false)
        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise(notes) }
    }

    /**
     * Holds the run, or lets it go again.
     *
     * Nothing is analysed while it is held, and on continuing the note is put up again from the
     * start: a reaction measured across a pause would be a reading of how long the player was away,
     * not of how long the note took. Wrong tries already made are kept.
     */
    fun togglePause() {
        if (!_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isPaused = pause.toggle())
    }

    /** Gives up on the note on screen and moves to the next one. */
    fun skip() {
        if (_uiState.value.isRunning) skipRequested = true
    }

    /** Ends the exercise early and keeps the notes that were played. */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        pause.resume()
        _uiState.value = _uiState.value.copy(isPaused = false)
        // A count-in has practised nothing, so there is no result to publish - and cancelling it
        // may happen before the exercise coroutine runs again, in which case nothing else would
        // move the state on.
        if (_uiState.value.phase is NoteTrainerPhase.CountIn) {
            _uiState.value = _uiState.value.copy(phase = NoteTrainerPhase.Idle)
        }
        job?.cancel()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(phase = NoteTrainerPhase.Idle, progress = null)
    }

    override fun onCleared() {
        stop()
        metronome?.release()
        super.onCleared()
    }

    private suspend fun runExercise(notes: List<Note>) = coroutineScope {
        val attempts = mutableListOf<NoteAttempt>()
        // Not a StateFlow: it would conflate, and frame after frame of silence is exactly the same
        // value. Every frame has to reach the note being played, because that is what makes a skip
        // or a pause take effect while nobody is making a sound.
        val heard = MutableSharedFlow<Hearing>(
            extraBufferCapacity = HEARD_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        var listenJob: Job? = null

        try {
            countIn()

            // Opened after the count, so a click can never be heard as a played note.
            listenJob = launch {
                NoteListener(audioCapture!!, config.capture, config.listening)
                    .listen()
                    .collect { heard.tryEmit(it) }
            }

            _uiState.value = _uiState.value.copy(phase = NoteTrainerPhase.Running)

            notes.forEachIndexed { index, note ->
                attempts += playNote(note, index, notes.size, heard)
            }

            finish(attempts, notes.size)
        } catch (cancellation: CancellationException) {
            // Stopped by the player or by leaving the screen: report what was played. A run that
            // never got past its count-in has nothing to report; stop() has already said so.
            if (_uiState.value.phase is NoteTrainerPhase.Running) {
                finish(attempts, notes.size)
            }
            throw cancellation
        } finally {
            listenJob?.cancel()
        }
    }

    /**
     * Puts one note up and waits for it.
     *
     * Every capture frame is one turn of this loop, so silence keeps it responsive: a skip or a
     * pause is picked up on the next frame rather than only when something is played.
     */
    private suspend fun playNote(
        note: Note,
        index: Int,
        noteCount: Int,
        heard: SharedFlow<Hearing>,
    ): NoteAttempt {
        val expected = _uiState.value.soundingNoteOf(note)
        skipRequested = false

        var presentedAt: Double? = seconds()
        var firstCorrectAt: Double? = null
        var correctSince: Double? = null
        var wrongSince: Double? = null
        var wrongCounted = false
        var lastSounding: Note? = null
        var wrongTries = 0
        var played = false

        publish(note, index, noteCount, Listening.WAITING, null, wrongTries)

        heard.transformWhile { hearing ->
            val now = seconds()

            if (pause.paused) {
                // Held: nothing is analysed and the note starts again on continuing, because a
                // reaction measured across a pause is a reading of how long the player was away.
                presentedAt = null
                firstCorrectAt = null
                correctSince = null
                wrongSince = null
                wrongCounted = false
                lastSounding = null
                emit(Unit)
                return@transformWhile true
            }
            if (presentedAt == null) {
                presentedAt = now
                publish(note, index, noteCount, Listening.WAITING, null, wrongTries)
            }

            val sounding = (hearing as? Hearing.Pitched)?.detected?.note
            val correct = sounding != null && sounding == expected

            when {
                correct -> {
                    if (firstCorrectAt == null) firstCorrectAt = now
                    if (correctSince == null) correctSince = now
                    wrongSince = null
                    wrongCounted = false
                }

                sounding != null -> {
                    correctSince = null
                    firstCorrectAt = null
                    if (sounding != lastSounding) {
                        wrongSince = now
                        wrongCounted = false
                    }
                    if (!wrongCounted && now - (wrongSince ?: now) >= config.wrongPitchSeconds) {
                        wrongTries++
                        wrongCounted = true
                    }
                }

                // A sound with no pitch in it says nothing either way, so it neither counts against
                // the player nor breaks a note they are already holding.
                hearing is Hearing.Unclear -> Unit

                else -> {
                    correctSince = null
                    firstCorrectAt = null
                    wrongSince = null
                    wrongCounted = false
                }
            }
            lastSounding = sounding

            played = correctSince?.let { now - it >= config.requiredCorrectSeconds } == true

            publish(
                note = note,
                index = index,
                noteCount = noteCount,
                listening = when {
                    correct -> Listening.CORRECT
                    sounding != null -> Listening.WRONG_PITCH
                    hearing is Hearing.Unclear -> Listening.UNCLEAR
                    else -> Listening.WAITING
                },
                detected = sounding,
                wrongTries = wrongTries,
                reactionSeconds = if (played) reactionOf(presentedAt, firstCorrectAt) else null,
            )

            emit(Unit)
            !played && !skipRequested
        }.collect { }

        if (skipRequested) {
            skipRequested = false
            return NoteAttempt(note, reactionSeconds = null, wrongTries = wrongTries, skipped = true)
        }

        // A moment on the green note before the next one, so the player sees it landed.
        delay((config.successSeconds * MILLIS_PER_SECOND).toLong())

        return NoteAttempt(
            note = note,
            reactionSeconds = reactionOf(presentedAt, firstCorrectAt),
            wrongTries = wrongTries,
            skipped = false,
        )
    }

    /** Measured to the *first* correct frame, so waiting to be sure never inflates it. */
    private fun reactionOf(presentedAt: Double?, firstCorrectAt: Double?): Double? =
        if (presentedAt == null || firstCorrectAt == null) null else firstCorrectAt - presentedAt

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
            publishCountIn(beats)
            held.leadIn(leadInBeatsFor(beats), config.tempo)
        }

        val engine = metronome?.takeIf { state.countInClick } ?: run {
            held.countIn(beats, config.tempo, ::publishCountIn)
            return
        }

        var counted = false
        while (!counted) {
            pause.isPaused.first { !it }
            counted = coroutineScope {
                val counting = launch { engine.clickCountIn(beats, config.tempo, ::publishCountIn) }
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
        _uiState.value = _uiState.value.copy(phase = NoteTrainerPhase.CountIn(beatsRemaining))
    }

    private fun publish(
        note: Note,
        index: Int,
        noteCount: Int,
        listening: Listening,
        detected: Note?,
        wrongTries: Int,
        reactionSeconds: Double? = null,
    ) {
        val state = _uiState.value
        _uiState.value = state.copy(
            progress = NoteTrainerProgress(
                noteIndex = index,
                noteCount = noteCount,
                currentNote = note,
                fingering = if (state.showFingeringHint) config.chart.fingeringFor(note) else null,
                listening = listening,
                detectedNote = detected?.let(state::writtenNoteOf),
                wrongTries = wrongTries,
                lastReactionSeconds = reactionSeconds,
            ),
        )
    }

    private fun finish(attempts: List<NoteAttempt>, plannedNoteCount: Int) {
        pause.resume()
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            phase = NoteTrainerPhase.Finished(
                NoteTrainerResult(
                    attempts = attempts.toList(),
                    plannedNoteCount = plannedNoteCount,
                ),
            ),
            progress = null,
        )
    }

    private fun seconds(): Double = nanoTime() / NANOS_PER_SECOND

    private fun updateSetup(change: (NoteTrainerUiState) -> NoteTrainerUiState) {
        if (_uiState.value.isBusy) return
        _uiState.value = change(_uiState.value)
    }

    private fun NoteTrainerUiState.withNoteSet(noteSet: NoteSet) = copy(
        noteSet = noteSet,
        activeNotes = NoteSets.notesOf(noteSet, config.chart, customNotes),
    )

    /**
     * Overlays a ready-made exercise. Only the run is configured: nothing is written back to the
     * player's settings, and the setup screen is skipped rather than pre-filled.
     */
    private fun NoteTrainerUiState.withPreset(preset: NoteTrainerPreset) = copy(
        activeNotes = preset.notes.filter(config.chart::supports),
        order = preset.order,
        rounds = preset.rounds,
        showFingeringHint = preset.showFingeringHint,
        targetSeconds = preset.targetSeconds,
        isPreconfigured = true,
    )

    private fun initialState(): NoteTrainerUiState {
        val notes = config.chart.supportedNotes
        val settings = settingsRepository.settings.value
        val state = NoteTrainerUiState(
            availableNotes = notes,
            noteSet = NoteSet.BEGINNER,
            customNotes = notes.toSet(),
            activeNotes = NoteSets.notesOf(NoteSet.BEGINNER, config.chart, notes.toSet()),
            order = NoteOrder.RANDOM,
            rounds = config.defaultRounds,
            targetSeconds = config.defaultTargetSeconds,
            targetOptions = config.targetOptions,
            transposition = settings.instrument.transposition,
            instrumentName = settings.instrument.displayName,
            noteNaming = settings.noteNaming,
            countInBeats = settings.countInBeats,
            countInClick = settings.countInClick,
        )
        return if (preset == null) state else state.withPreset(preset)
    }

    private companion object {
        const val HEARD_BUFFER = 4
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MILLIS_PER_SECOND = 1_000
    }
}

package com.example.saxtrainer.feature.phrasetrainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.listening.Hearing
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.exercise.BeatClock
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.PauseSwitch
import com.example.saxtrainer.core.exercise.countIn
import com.example.saxtrainer.core.exercise.leadIn
import com.example.saxtrainer.core.exercise.leadInBeatsFor
import com.example.saxtrainer.core.exercise.monotonicNanos
import com.example.saxtrainer.core.exercise.pausedBy
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.spelledAs
import com.example.saxtrainer.core.phrase.PatternCatalog
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseCatalog
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.PracticeUnit
import com.example.saxtrainer.core.phrase.repeated
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * Runs a phrase trainer exercise: show a unit, hide it, play it back from memory, next unit.
 *
 * Driven by what the microphone hears rather than by a clock, the same architectural choice the
 * note trainer makes - a note waits as long as the player needs, and what is measured is whether it
 * was played, never how long it took to think of. Pitch-correctness is a per-frame comparison of
 * heard against expected, exactly as the note trainer computes it; whether a note also has to be
 * *held* for its written length is a separate, later question - [PhraseTrainerUiState.checkDuration]
 * only changes how long the floor timer has to run, never how the pitch comparison itself works.
 *
 * A rest needs nothing played and is stepped over without listening for it.
 */
class PhraseTrainerViewModel(
    private val settingsRepository: SettingsRepository,
    /** What this trainer was last started with. Defaulted so a trainer without one still runs. */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val audioCapture: AudioCapture? = null,
    private val phraseCatalog: PhraseCatalog = PhraseLibrary,
    private val patternCatalog: PatternCatalog = PatternLibrary,
    private val preset: PhraseTrainerPreset? = null,
    private val config: PhraseTrainerConfig = PhraseTrainerConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val countInClock: BeatClock = MonotonicBeatClock(),
    /** Injectable so tests can measure timing on virtual time. */
    private val nanoTime: () -> Long = monotonicNanos,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<PhraseTrainerUiState> = _uiState.asStateFlow()

    private var exerciseJob: Job? = null
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

    /** Opens the phrase-library window - a plain list, nothing to edit. */
    fun openLibraryPicker() = updateSetup { it.copy(libraryPickerOpen = true) }
    fun closeLibraryPicker() = updateSetup { it.copy(libraryPickerOpen = false) }

    fun selectLibraryPhrase(id: String) = updateSetup { state ->
        state.copy(sourceType = PhraseSourceType.LIBRARY, libraryPhraseId = id, libraryPickerOpen = false)
    }

    /** Opens the scale-pattern window: octave, scale, and the stored patterns to choose between. */
    fun openPatternPicker() = updateSetup { it.copy(patternPickerOpen = true) }
    fun closePatternPicker() = updateSetup { it.copy(patternPickerOpen = false) }

    fun selectScaleChoice(id: String) = updateSetup { it.copy(scaleChoiceId = id) }

    /** Adds or drops [octave] from the octaves a scale pattern is generated in. */
    fun toggleOctave(octave: Int) = updateSetup { state ->
        if (octave !in PhraseTrainerUiState.OCTAVE_RANGE) return@updateSetup state
        val octaves = if (octave in state.octaves) state.octaves - octave else state.octaves + octave
        state.copy(octaves = octaves)
    }

    fun selectPattern(id: String) = updateSetup { state ->
        state.copy(sourceType = PhraseSourceType.SCALE_PATTERN, patternId = id, patternPickerOpen = false)
    }

    fun selectRepeats(times: Int) = updateSetup { it.copy(repeats = times.coerceIn(it.repeatRange)) }

    /** Whether every repeat's units come from one shuffled pool instead of one pass at a time. */
    fun setMixed(enabled: Boolean) = updateSetup { it.copy(mixed = enabled) }

    /** How long a unit stays on screen before it is hidden and has to be played from memory. */
    fun setShowSeconds(seconds: Double) = updateSetup {
        it.copy(showSeconds = seconds.coerceIn(PhraseTrainerUiState.SHOW_SECONDS_RANGE))
    }

    /** Whether note length is also checked while playing, not just which notes were played. */
    fun setCheckDuration(check: Boolean) = updateSetup { it.copy(checkDuration = check) }

    /** Called by the screen when the microphone was asked for and refused. */
    fun onMicrophoneUnavailable() {
        _uiState.value = _uiState.value.copy(microphoneUnavailable = true)
    }

    /**
     * Writes the current setup down now, without waiting for it to be changed or started - see the
     * phrase quiz's own `rememberSetupNow` for why.
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
        val phrase = state.phrase
        if (!state.canStart || audioCapture == null || state.units.isEmpty() || phrase == null) return
        val units = state.units.repeated(state.repeats, state.mixed, random)

        rememberSetup(state)
        pause.resume()
        skipRequested = false
        _uiState.value = _uiState.value.copy(isPaused = false)
        exerciseJob = viewModelScope.launch(processingDispatcher) { runExercise(units) }
    }

    /** Holds the run, or lets it go again. See the note trainer's own `togglePause` for why. */
    fun togglePause() {
        if (!_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isPaused = pause.toggle())
    }

    /** Gives up on the note on screen and moves to the next one. */
    fun skip() {
        if (_uiState.value.isRunning) skipRequested = true
    }

    /** Ends the exercise early and keeps the units that were attempted. */
    fun stop() {
        val job = exerciseJob
        exerciseJob = null
        pause.resume()
        _uiState.value = _uiState.value.copy(isPaused = false)
        if (_uiState.value.phase is PhraseTrainerPhase.CountIn) {
            _uiState.value = _uiState.value.copy(phase = PhraseTrainerPhase.Idle)
        }
        job?.cancel()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        _uiState.value = _uiState.value.copy(phase = PhraseTrainerPhase.Idle, progress = null)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private suspend fun runExercise(units: List<PracticeUnit>) = coroutineScope {
        val phrase = _uiState.value.phrase ?: return@coroutineScope
        val attempts = mutableListOf<UnitAttempt>()
        // Not a StateFlow: it would conflate, and frame after frame of silence is exactly the same
        // value. Every frame has to reach the note being played, so a skip or a pause takes effect
        // while nobody is making a sound.
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

            units.forEachIndexed { unitIndex, unit ->
                _uiState.value = _uiState.value.copy(phase = PhraseTrainerPhase.Showing(unitIndex))
                delay((_uiState.value.showSeconds * MILLIS_PER_SECOND).toLong())

                _uiState.value = _uiState.value.copy(phase = PhraseTrainerPhase.Listening)
                attempts += playUnit(unitIndex, units.size, phrase.toneSequenceOf(unit), heard)
            }

            finish(attempts, units.size)
        } catch (cancellation: CancellationException) {
            // Stopped by the player or by leaving the screen: report what was played. A run that
            // never got past showing its first unit has nothing worth reporting.
            if (_uiState.value.phase is PhraseTrainerPhase.Listening) {
                finish(attempts, units.size)
            }
            throw cancellation
        } finally {
            listenJob?.cancel()
        }
    }

    /** Plays every step of one unit in turn, rests included - stepped over rather than listened for. */
    private suspend fun playUnit(
        unitIndex: Int,
        unitCount: Int,
        sequence: ToneSequence,
        heard: SharedFlow<Hearing>,
    ): UnitAttempt {
        val steps = sequence.steps
        val attempts = steps.mapIndexed { stepIndex, step ->
            // Asked for as written: the phrase's own E flat, not whichever spelling its pitch has.
            val note = sequence.writtenAt(stepIndex)
            if (note == null) {
                StepAttempt(note = null, played = true)
            } else {
                // What is heard is a pitch. It is written as the note asked for when it is that
                // pitch, as another note of the unit sounding the same, or else in the phrase's own
                // style - never in a spelling the phrase would not use.
                val spell = { heardNote: Note ->
                    heardNote.spelledAs(listOf(note) + sequence.writtenNotes, sequence.accidentalStyle)
                }
                playStep(note, step.duration, unitIndex, unitCount, stepIndex, steps.size, heard, spell)
            }
        }
        return UnitAttempt(attempts)
    }

    /**
     * Puts one note up and waits for it.
     *
     * Every capture frame is one turn of this loop, so silence keeps it responsive: a skip or a
     * pause is picked up on the next frame rather than only when something is played.
     */
    private suspend fun playStep(
        note: SpelledNote,
        duration: Beats,
        unitIndex: Int,
        unitCount: Int,
        stepIndex: Int,
        stepCount: Int,
        heard: SharedFlow<Hearing>,
        spell: (Note) -> SpelledNote,
    ): StepAttempt {
        val state = _uiState.value
        val expected = state.soundingNoteOf(note.note)
        // The duration check is a floor on top of the pitch comparison, never a change to it: a
        // note that is never right is never right regardless of how long it would have had to be
        // held. Turning the check off simply asks for less time on top of the same comparison.
        val requiredSeconds = if (state.checkDuration) {
            maxOf(config.requiredCorrectSeconds, state.tempo.secondsFor(duration))
        } else {
            config.requiredCorrectSeconds
        }
        skipRequested = false

        var correctSince: Double? = null
        // One frame reporting something other than the right pitch - a reed squeak, a harmonic the
        // detector snapped onto for an instant - is not the same as the note actually changing. Only
        // a second one in a row, with no correct frame between, is trusted to mean that: see
        // BAD_FRAME_TOLERANCE.
        var consecutiveBadFrames = 0
        var played = false

        publish(unitIndex, unitCount, stepIndex, stepCount, note, Listening.WAITING, null, false, spell)

        heard.transformWhile { hearing ->
            if (pause.paused) {
                correctSince = null
                consecutiveBadFrames = 0
                emit(Unit)
                return@transformWhile true
            }

            val now = seconds()
            val sounding = (hearing as? Hearing.Pitched)?.detected?.note
            val correct = sounding != null && sounding == expected

            when {
                correct -> {
                    if (correctSince == null) correctSince = now
                    consecutiveBadFrames = 0
                }

                // A sound with no pitch in it says nothing either way: never breaks a hold, unlike
                // a wrong note actually being played.
                hearing is Hearing.Unclear -> Unit

                else -> {
                    consecutiveBadFrames++
                    if (consecutiveBadFrames >= BAD_FRAME_TOLERANCE) correctSince = null
                }
            }

            val heldSeconds = correctSince?.let { now - it }
            played = heldSeconds != null && heldSeconds >= requiredSeconds

            publish(
                unitIndex = unitIndex,
                unitCount = unitCount,
                stepIndex = stepIndex,
                stepCount = stepCount,
                note = note,
                listening = when {
                    correct -> Listening.CORRECT
                    sounding != null -> Listening.WRONG_PITCH
                    hearing is Hearing.Unclear -> Listening.UNCLEAR
                    else -> Listening.WAITING
                },
                detected = sounding,
                landed = played,
                spell = spell,
            )

            emit(Unit)
            !played && !skipRequested
        }.collect { }

        if (skipRequested) {
            skipRequested = false
            return StepAttempt(note, played = false)
        }

        // A moment on the green note before the next one, so the player sees it landed.
        delay((config.successSeconds * MILLIS_PER_SECOND).toLong())
        return StepAttempt(note, played = true)
    }

    /** Counts the player in, silently: this trainer makes no click of its own to keep. */
    private suspend fun countIn() {
        val state = _uiState.value
        val beats = state.countInBeats
        if (beats <= 0) return

        val held = countInClock.pausedBy(pause)
        publishCountIn(beats)
        held.leadIn(leadInBeatsFor(beats), state.tempo)
        held.countIn(beats, state.tempo, ::publishCountIn)
    }

    private fun publishCountIn(beatsRemaining: Int) {
        _uiState.value = _uiState.value.copy(phase = PhraseTrainerPhase.CountIn(beatsRemaining))
    }

    private fun publish(
        unitIndex: Int,
        unitCount: Int,
        stepIndex: Int,
        stepCount: Int,
        note: SpelledNote,
        listening: Listening,
        detected: Note?,
        landed: Boolean,
        spell: (Note) -> SpelledNote,
    ) {
        val state = _uiState.value
        _uiState.value = state.copy(
            progress = PhraseTrainerProgress(
                unitIndex = unitIndex,
                unitCount = unitCount,
                stepIndex = stepIndex,
                stepCount = stepCount,
                currentNote = note,
                listening = listening,
                detectedNote = detected?.let(state::writtenNoteOf)?.let(spell),
                landed = landed,
            ),
        )
    }

    private fun finish(attempts: List<UnitAttempt>, plannedCount: Int) {
        pause.resume()
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            phase = PhraseTrainerPhase.Finished(PhraseTrainerResult(attempts.toList(), plannedCount)),
            progress = null,
        )
    }

    private fun seconds(): Double = nanoTime() / NANOS_PER_SECOND

    private fun updateSetup(change: (PhraseTrainerUiState) -> PhraseTrainerUiState) {
        if (_uiState.value.isBusy) return
        val changed = change(_uiState.value)
        _uiState.value = changed
        rememberSetup(changed)
    }

    /**
     * Writes the setup down as it is changed, not only when a run begins - a setup screen is left
     * by going back, which throws this ViewModel away, so anything not written down by then is
     * gone.
     */
    private fun rememberSetup(state: PhraseTrainerUiState) {
        if (state.isPreconfigured) return
        exerciseSetups.remember(Exercise.PHRASE_TRAINER, state.toSetup())
    }

    private fun initialState(): PhraseTrainerUiState {
        val library = phraseCatalog.all
        val patterns = patternCatalog.all
        val settings = settingsRepository.settings.value
        // Something is always selected, the way a fresh sequence trainer opens on its first scale.
        val state = PhraseTrainerUiState(
            library = library,
            libraryPhraseId = library.firstOrNull()?.id,
            patterns = patterns,
            patternId = patterns.firstOrNull()?.id,
            showSeconds = config.defaultShowSeconds,
            tempo = config.defaultTempo,
            transposition = settings.instrument.transposition,
            instrumentName = settings.instrument.displayName,
            noteNaming = settings.noteNaming,
            countInBeats = settings.countInBeats,
            countInClick = settings.countInClick,
        )

        return if (preset != null) {
            state.withPreset(preset, phraseCatalog, patternCatalog)
        } else {
            state.withSetup(exerciseSetups.setupOf(Exercise.PHRASE_TRAINER), phraseCatalog, patternCatalog)
        }
    }

    private companion object {
        const val HEARD_BUFFER = 4
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MILLIS_PER_SECOND = 1_000

        /**
         * Consecutive wrong-or-silent frames needed before a hold in progress is thrown away. One
         * is a glitch; a real change of note or a real silence still reads as one at the next frame,
         * roughly 40 ms later, so this costs nothing when the note genuinely did change.
         */
        const val BAD_FRAME_TOLERANCE = 2
    }
}

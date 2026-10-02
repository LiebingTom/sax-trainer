package com.example.saxtrainer.feature.phrasequiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.ToneStep
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
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs a phrase quiz: one practice unit shown, hidden, then rebuilt by tapping its notes back into
 * order, one unit after another until the phrase is walked.
 *
 * There is no microphone here: this quiz asks whether the player can hear and remember an order,
 * not whether they can play it back - see the Phrase Trainer for that half of the same content. A
 * [preset] hands the quiz a ready-made phrase instead of asking the player to build one; the quiz
 * neither knows nor cares where it came from.
 */
class PhraseQuizViewModel(
    private val settingsRepository: SettingsRepository,
    /** What this quiz was last started with. Defaulted so a quiz without one still runs. */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val phraseCatalog: PhraseCatalog = PhraseLibrary,
    private val patternCatalog: PatternCatalog = PatternLibrary,
    private val preset: PhraseQuizPreset? = null,
    private val config: PhraseQuizConfig = PhraseQuizConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<PhraseQuizUiState> = _uiState.asStateFlow()

    private val attempted = mutableListOf<Boolean>()
    private var showJob: Job? = null

    /**
     * The units this run actually walks: [PhraseQuizUiState.units] repeated and, if
     * [PhraseQuizUiState.mixed] asked for it, shuffled - decided once, in [start], and never
     * touched again for the rest of the run. See [repeated].
     */
    private var runUnits: List<PracticeUnit> = emptyList()

    /** A preset that asked to start does so once, not again on every recomposition or rotation. */
    private var autoStartPending = preset?.autoStart == true

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(noteNaming = settings.noteNaming)
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
        if (octave !in PhraseQuizUiState.OCTAVE_RANGE) return@updateSetup state
        val octaves = if (octave in state.octaves) state.octaves - octave else state.octaves + octave
        state.copy(octaves = octaves)
    }

    fun selectPattern(id: String) = updateSetup { state ->
        state.copy(sourceType = PhraseSourceType.SCALE_PATTERN, patternId = id, patternPickerOpen = false)
    }

    fun selectRepeats(times: Int) = updateSetup { it.copy(repeats = times.coerceIn(it.repeatRange)) }

    /** Whether every repeat's units come from one shuffled pool instead of one pass at a time. */
    fun setMixed(enabled: Boolean) = updateSetup { it.copy(mixed = enabled) }

    /**
     * How long a unit stays on screen before it is hidden and has to be rebuilt from memory.
     *
     * Infinite - shown until [revealNow] is called by hand - is only ever reached by asking for it
     * exactly: an out-of-range number lands on the largest timed step instead, so a stray or
     * corrupted value never silently turns into "forever" on its own.
     */
    fun setShowSeconds(seconds: Double) = updateSetup {
        val options = PhraseQuizUiState.SHOW_SECONDS_OPTIONS
        val largestTimed = options[options.lastIndex - 1]
        val clamped = if (seconds.isInfinite()) Double.POSITIVE_INFINITY else seconds.coerceIn(options.first(), largestTimed)
        it.copy(showSeconds = clamped)
    }

    /**
     * Writes the current setup down now, without waiting for it to be changed or started.
     *
     * A practice nobody has touched is still a practice: "add to your lessons" has to be able to
     * ask for it, exactly as the note quiz's own `rememberSetupNow` does.
     */
    fun rememberSetupNow() = rememberSetup(_uiState.value)

    fun consumeAutoStart(): Boolean {
        if (!autoStartPending) return false
        autoStartPending = false
        return true
    }

    fun start() {
        val state = _uiState.value
        if (state.isRunning || !state.canStart) return

        rememberSetup(state)
        showJob?.cancel()
        attempted.clear()
        runUnits = state.units.repeated(state.repeats, state.mixed, random)
        beginUnit(0)
    }

    /** Places [option] - an index into the current unit's shuffled options - next in the order. */
    fun choose(option: Int) {
        when (val phase = _uiState.value.phase) {
            is PhraseQuizPhase.Answered -> continueFrom(phase)

            is PhraseQuizPhase.Asking -> {
                if (option !in phase.options.indices || option in phase.chosen) return
                val taken = phase.copy(chosen = phase.chosen + option)
                _uiState.value = _uiState.value.copy(phase = taken)
                if (taken.isSettled) settle(taken)
            }

            else -> Unit
        }
    }

    /** Frees the last note placed, putting it back among the choices - only while still building. */
    fun undoLast() {
        val phase = _uiState.value.phase as? PhraseQuizPhase.Asking ?: return
        if (phase.chosen.isEmpty()) return
        _uiState.value = _uiState.value.copy(phase = phase.copy(chosen = phase.chosen.dropLast(1)))
    }

    /**
     * Hides the unit and starts asking for it right now, cutting the preview short - the one way to
     * move on at all while it is shown until pressed, and, whatever the preview time is set to, a
     * player who has already seen enough should never have to wait out the rest of it.
     */
    fun revealNow() {
        val phase = _uiState.value.phase as? PhraseQuizPhase.Showing ?: return
        showJob?.cancel()
        showJob = null
        beginAsking(phase.unitIndex, phase.correct)
    }

    /**
     * Moves on from a settled unit, right or wrong - there is no timer doing this on its own: a
     * phrase can take longer to read back than a single note does, so it is the player who decides
     * when they are done looking at it.
     */
    fun continueToNextUnit() {
        val phase = _uiState.value.phase
        if (phase is PhraseQuizPhase.Answered) continueFrom(phase)
    }

    /** Ends the quiz early and keeps the units that were attempted. */
    fun stop() {
        showJob?.cancel()
        showJob = null
        if (_uiState.value.isRunning) finish()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        attempted.clear()
        _uiState.value = _uiState.value.copy(phase = PhraseQuizPhase.Idle)
    }

    override fun onCleared() {
        showJob?.cancel()
        super.onCleared()
    }

    private fun beginUnit(index: Int) {
        val state = _uiState.value
        val phrase = state.phrase
        if (phrase == null || index >= runUnits.size) {
            finish()
            return
        }

        // Each step keeps the spelling the phrase gives it, so the tiles it becomes - and the line
        // they are put back into - are written the same way the phrase was shown.
        val steps = phrase.toneSequenceOf(runUnits[index]).spelledSteps()
        _uiState.value = state.copy(phase = PhraseQuizPhase.Showing(index, steps))
        // Infinite is shown until revealNow() is called by hand; anything else times itself out.
        if (state.showSeconds.isFinite()) {
            showJob = viewModelScope.launch(processingDispatcher) {
                delay((state.showSeconds * MILLIS_PER_SECOND).toLong())
                beginAsking(index, steps)
            }
        }
    }

    private fun beginAsking(index: Int, steps: List<ToneStep>) {
        // The phase may already have moved on - Stop and the waiting job can both arrive.
        if ((_uiState.value.phase as? PhraseQuizPhase.Showing)?.unitIndex != index) return
        // Padded with near-pitch decoys, never fewer of the unit's own notes than it has: rebuilding
        // the order is still finding every real note exactly once, just among tiles that are not all
        // real.
        val style = _uiState.value.phrase?.accidentalStyle ?: AccidentalStyle.SHARPS
        val decoys = decoysFor(steps.mapNotNull { it.note }, config.optionGridSize, random)
            .map { ToneStep.of(SpelledNote.of(it, style), DECOY_DURATION) }
        _uiState.value = _uiState.value.copy(
            phase = PhraseQuizPhase.Asking(unitIndex = index, correct = steps, options = (steps + decoys).shuffled(random)),
        )
    }

    /** The last note is in: the unit is judged, kept, and left up until the player moves on. */
    private fun settle(taken: PhraseQuizPhase.Asking) {
        val answered = PhraseQuizPhase.Answered(taken.unitIndex, taken.correct, taken.options, taken.chosen)
        attempted += answered.correctOrder
        _uiState.value = _uiState.value.copy(phase = answered)
    }

    private fun continueFrom(answered: PhraseQuizPhase.Answered) {
        // The phase may already have moved on - a second tap can still arrive after the first.
        if (_uiState.value.phase !== answered) return
        beginUnit(answered.unitIndex + 1)
    }

    private fun finish() {
        val planned = runUnits.size.takeIf { it > 0 } ?: attempted.size
        _uiState.value = _uiState.value.copy(
            phase = PhraseQuizPhase.Finished(PhraseQuizResult(attempted.toList(), planned)),
        )
    }

    private fun updateSetup(change: (PhraseQuizUiState) -> PhraseQuizUiState) {
        if (_uiState.value.isRunning) return
        val changed = change(_uiState.value)
        _uiState.value = changed
        rememberSetup(changed)
    }

    /**
     * Writes the setup down as it is changed, not only when a run begins - a setup screen is left
     * by going back, which throws this ViewModel away, so anything not written down by then is
     * gone.
     */
    private fun rememberSetup(state: PhraseQuizUiState) {
        if (state.isPreconfigured) return
        exerciseSetups.remember(Exercise.PHRASE_QUIZ, state.toSetup())
    }

    private fun initialState(): PhraseQuizUiState {
        val library = phraseCatalog.all
        val patterns = patternCatalog.all
        // Something is always selected, the way a fresh sequence trainer opens on its first
        // scale: an empty setup screen with nothing to start is a worse default than a guess.
        val state = PhraseQuizUiState(
            library = library,
            libraryPhraseId = library.firstOrNull()?.id,
            patterns = patterns,
            patternId = patterns.firstOrNull()?.id,
            showSeconds = config.defaultShowSeconds,
        )
        return if (preset != null) {
            state.withPreset(preset, phraseCatalog, patternCatalog)
        } else {
            state.withSetup(exerciseSetups.setupOf(Exercise.PHRASE_QUIZ), phraseCatalog, patternCatalog)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000

        /** A decoy has no correct place, so its written length is never asked about - any will do. */
        val DECOY_DURATION = Beats.of(1)
    }
}

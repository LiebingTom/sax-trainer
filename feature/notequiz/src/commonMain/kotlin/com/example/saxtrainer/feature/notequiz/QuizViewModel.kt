package com.example.saxtrainer.feature.notequiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.SpelledNote
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
 * Runs a note quiz: a question, an answer, a moment to see whether it was right, the next question.
 *
 * There is no clock and no microphone. A question waits as long as the player needs, and the only
 * timed thing in here is how long the answer stays on screen afterwards - short enough that a
 * quick session stays quick, and skippable by tapping again.
 *
 * A [preset] hands the quiz a ready-made run instead of asking the player to build one. The quiz
 * neither knows nor cares where it came from.
 */
class QuizViewModel(
    private val settingsRepository: SettingsRepository,
    /**
     * What this quiz was last started with. Defaulted so a quiz without one still runs; it simply
     * opens on the plain defaults every time.
     */
    private val exerciseSetups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    private val preset: QuizPreset? = null,
    private val config: QuizConfig = QuizConfig(),
    private val random: Random = Random.Default,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val answers = mutableListOf<AnsweredQuestion>()
    private var revealJob: Job? = null

    /** A preset that asked to start does so once, not again on every recomposition or rotation. */
    private var autoStartPending = preset?.autoStart == true

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(noteNaming = settings.noteNaming)
            }
        }
    }

    fun selectNoteSet(noteSet: NoteSet) = updateSetup { it.withNoteSet(noteSet) }

    fun editCustomNotes() = updateSetup { it.copy(customNotesDraft = it.customNotes) }

    fun toggleCustomNote(note: SpelledNote) = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotesDraft = if (note in draft) draft - note else draft + note)
    }

    /** Every pitch once, written the way it usually is - not every spelling of it. */
    fun selectAllCustomNotes() = updateSetup {
        it.copy(customNotesDraft = NoteSets.notesOf(NoteSet.CHROMATIC, config.chart, emptySet()).toSet())
    }

    fun clearCustomNotes() = updateSetup { it.copy(customNotesDraft = emptySet()) }

    fun applyCustomNotes() = updateSetup { state ->
        val draft = state.customNotesDraft ?: return@updateSetup state
        state.copy(customNotes = draft, customNotesDraft = null).withNoteSet(NoteSet.CUSTOM)
    }

    fun cancelCustomNotes() = updateSetup { it.copy(customNotesDraft = null) }

    fun selectMode(mode: QuizMode) = updateSetup { it.copy(mode = mode) }

    fun selectQuestionCount(count: Int) = updateSetup { it.copy(questionCount = count) }

    fun selectSuccessDelaySeconds(seconds: Double) = updateSetup {
        it.copy(successDelaySeconds = seconds.coerceAtLeast(0.0))
    }

    /**
     * Cuts the wait after a right answer short and goes straight to the next question.
     *
     * Only heard while a right answer is on screen, so a tap can never stand in for an answer: what
     * a question is answered with is an option, never the panel around it. A wrong answer keeps its
     * own wait, because there is something to read in it.
     */
    fun skipSuccessDelay() {
        val phase = _uiState.value.phase
        if (phase is QuizPhase.Answered && phase.correct) continueFrom(phase)
    }

    /** Whether the screen should start without being asked, answered true only once. */
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
        val state = _uiState.value
        if (state.isRunning || !state.canStart) return

        // Already written down as it was chosen; this is the setup as it is actually
        // started.
        rememberSetup(state)

        revealJob?.cancel()
        answers.clear()
        ask(index = 0)
    }

    /** Takes an answer to the first slot, which is the only one most modes have. */
    fun answer(index: Int) = answer(slot = 0, option = index)

    /**
     * Takes [option] as the answer in [slot] and shows how it went.
     *
     * A slot is answered once: what was chosen stands, right or wrong, and further taps on it are
     * ignored. The question itself is over only when every slot has been answered, so a mode that
     * asks two things waits for both before anything is judged, recorded or timed.
     *
     * Answering while a settled question is already on screen skips the rest of the wait instead of
     * counting twice - the one thing that must never make a quick session slow is being made to
     * watch it.
     */
    fun answer(slot: Int, option: Int) {
        when (val phase = _uiState.value.phase) {
            is QuizPhase.Answered -> continueFrom(phase)

            is QuizPhase.Asking -> {
                val options = phase.question.answers.getOrNull(slot) ?: return
                if (option !in 0 until options.optionCount) return
                if (phase.chosen[slot] != null) return

                val taken = phase.taking(slot, option)
                _uiState.value = _uiState.value.copy(phase = taken)
                if (taken.isSettled) settle(taken)
            }

            else -> Unit
        }
    }

    /** The last slot is in: the question is judged, kept, and left up for its moment. */
    private fun settle(taken: QuizPhase.Asking) {
        val answered = QuizPhase.Answered(
            question = taken.question,
            index = taken.index,
            chosen = taken.chosen.map { it!! },
        )
        answers += AnsweredQuestion(answered.question.note, answered.correct)
        _uiState.value = _uiState.value.copy(phase = answered)

        // Asked for no wait at all: the next question rather than a frame of the answer.
        val millis = revealMillisOf(answered)
        if (millis <= 0) {
            continueFrom(answered)
            return
        }

        revealJob = viewModelScope.launch(processingDispatcher) {
            delay(millis)
            continueFrom(answered)
        }
    }

    /** Ends the quiz early and keeps the questions that were answered. */
    fun stop() {
        revealJob?.cancel()
        revealJob = null
        if (_uiState.value.isRunning) finish()
    }

    /** Leaves the result and returns to the setup screen. */
    fun reset() {
        stop()
        answers.clear()
        _uiState.value = _uiState.value.copy(phase = QuizPhase.Idle)
    }

    override fun onCleared() {
        revealJob?.cancel()
        super.onCleared()
    }

    private fun continueFrom(answered: QuizPhase.Answered) {
        revealJob?.cancel()
        revealJob = null
        // The phase may already have moved on - a second tap and the waiting job can both arrive.
        if (_uiState.value.phase !== answered) return

        val next = answered.index + 1
        if (next < _uiState.value.questionCount) ask(next) else finish()
    }

    private fun ask(index: Int) {
        val state = _uiState.value
        // Still showing the question just answered, if there was one: the next is never about the
        // same note.
        val previous = (state.phase as? QuizPhase.Answered)?.question?.note
        _uiState.value = state.copy(
            phase = QuizPhase.Asking(
                question = QuizQuestionGenerator.generate(
                    pool = state.activeNotes,
                    mode = state.mode,
                    chart = config.chart,
                    random = random,
                    previous = previous,
                ),
                index = index,
            ),
        )
    }

    private fun finish() {
        _uiState.value = _uiState.value.copy(
            phase = QuizPhase.Finished(
                QuizResult(
                    answers = answers.toList(),
                    plannedCount = _uiState.value.questionCount,
                ),
            ),
        )
    }

    private fun revealMillisOf(answered: QuizPhase.Answered): Long {
        val seconds = if (answered.correct) {
            _uiState.value.successDelaySeconds
        } else {
            config.revealWrongSeconds
        }
        return (seconds * MILLIS_PER_SECOND).toLong()
    }

    private fun updateSetup(change: (QuizUiState) -> QuizUiState) {
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
    private fun rememberSetup(state: QuizUiState) {
        if (state.isPreconfigured) return
        exerciseSetups.remember(Exercise.NOTE_QUIZ, state.toSetup())
    }

    private fun QuizUiState.withNoteSet(noteSet: NoteSet) = copy(
        noteSet = noteSet,
        activeNotes = NoteSets.notesOf(noteSet, config.chart, customNotes),
    )

    /**
     * Overlays a ready-made quiz. Only the run is configured: nothing is written back to the
     * player's settings.
     *
     * The setup screen is skipped rather than pre-filled, unless the preset asks to be edited - and
     * then the note set has to be filled in too, or the screen would claim a set it is not asking
     * about: a preset names notes, so as something to edit it is always a custom set.
     */
    private fun QuizUiState.withPreset(preset: QuizPreset): QuizUiState {
        val notes = preset.notes.filter { config.chart.supports(it.note) }
        return copy(
            noteSet = if (preset.editable) NoteSet.CUSTOM else noteSet,
            customNotes = if (preset.editable) notes.toSet() else customNotes,
            activeNotes = notes,
            mode = preset.mode,
            questionCount = preset.questionCount,
            successDelaySeconds = preset.successDelaySeconds,
            isPreconfigured = !preset.editable,
        )
    }

    private fun initialState(): QuizUiState {
        val everyPitch = NoteSets.notesOf(NoteSet.CHROMATIC, config.chart, emptySet()).toSet()
        val state = QuizUiState(
            availableNotes = NoteSets.choicesOf(config.chart),
            noteSet = NoteSet.BEGINNER,
            customNotes = everyPitch,
            activeNotes = NoteSets.notesOf(NoteSet.BEGINNER, config.chart, everyPitch),
            questionCount = config.defaultQuestionCount,
            questionCounts = config.questionCounts,
            successDelaySeconds = config.defaultSuccessDelaySeconds,
            successDelayOptions = config.successDelayOptions,
            noteNaming = settingsRepository.settings.value.noteNaming,
        )
        // A preset decides everything the setup screen would have asked, so a remembered setup has
        // nothing left to say about it.
        return if (preset != null) {
            state.withPreset(preset)
        } else {
            state.withSetup(exerciseSetups.setupOf(Exercise.NOTE_QUIZ), config.chart)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000
    }
}

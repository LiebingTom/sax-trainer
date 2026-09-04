package com.example.saxtrainer.feature.notequiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.music.Note
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

    fun selectMode(mode: QuizMode) = updateSetup { it.copy(mode = mode) }

    fun selectQuestionCount(count: Int) = updateSetup { it.copy(questionCount = count) }

    /** Whether the screen should start without being asked, answered true only once. */
    fun consumeAutoStart(): Boolean {
        if (!autoStartPending) return false
        autoStartPending = false
        return true
    }

    fun start() {
        val state = _uiState.value
        if (state.isRunning || !state.canStart) return

        revealJob?.cancel()
        answers.clear()
        ask(index = 0)
    }

    /**
     * Takes the answer at [index] and shows how it went.
     *
     * Answering while an answer is already on screen skips the rest of the wait instead of counting
     * twice - the one thing that must never make a quick session slow is being made to watch it.
     */
    fun answer(index: Int) {
        when (val phase = _uiState.value.phase) {
            is QuizPhase.Answered -> continueFrom(phase)

            is QuizPhase.Asking -> {
                if (index !in 0 until phase.question.optionCount) return
                val answered = QuizPhase.Answered(phase.question, phase.index, index)
                answers += AnsweredQuestion(phase.question.note, answered.correct)
                _uiState.value = _uiState.value.copy(phase = answered)

                revealJob = viewModelScope.launch(processingDispatcher) {
                    delay(revealMillisOf(answered))
                    continueFrom(answered)
                }
            }

            else -> Unit
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
        _uiState.value = state.copy(
            phase = QuizPhase.Asking(
                question = QuizQuestionGenerator.generate(
                    pool = state.activeNotes,
                    mode = state.mode,
                    chart = config.chart,
                    random = random,
                    optionCount = config.optionCount,
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
        val seconds =
            if (answered.correct) config.revealCorrectSeconds else config.revealWrongSeconds
        return (seconds * MILLIS_PER_SECOND).toLong()
    }

    private fun updateSetup(change: (QuizUiState) -> QuizUiState) {
        if (_uiState.value.isRunning) return
        _uiState.value = change(_uiState.value)
    }

    private fun QuizUiState.withNoteSet(noteSet: NoteSet) = copy(
        noteSet = noteSet,
        activeNotes = NoteSets.notesOf(noteSet, config.chart, customNotes),
    )

    /**
     * Overlays a ready-made quiz. Only the run is configured: nothing is written back to the
     * player's settings, and the setup screen is skipped rather than pre-filled.
     */
    private fun QuizUiState.withPreset(preset: QuizPreset) = copy(
        activeNotes = preset.notes.filter(config.chart::supports),
        mode = preset.mode,
        questionCount = preset.questionCount,
        isPreconfigured = true,
    )

    private fun initialState(): QuizUiState {
        val notes = config.chart.supportedNotes
        val state = QuizUiState(
            availableNotes = notes,
            noteSet = NoteSet.BEGINNER,
            customNotes = notes.toSet(),
            activeNotes = NoteSets.notesOf(NoteSet.BEGINNER, config.chart, notes.toSet()),
            questionCount = config.defaultQuestionCount,
            questionCounts = config.questionCounts,
            noteNaming = settingsRepository.settings.value.noteNaming,
        )
        return if (preset == null) state else state.withPreset(preset)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000
    }
}

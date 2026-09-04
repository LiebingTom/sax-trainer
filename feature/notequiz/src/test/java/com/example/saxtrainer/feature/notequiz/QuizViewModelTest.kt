package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Running a quiz: a question, an answer, the next question. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = QuizConfig()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state

        override fun setInstrument(instrument: Instrument) {
            state.value = state.value.copy(instrument = instrument)
        }

        override fun setNoteNaming(naming: NoteNaming) {
            state.value = state.value.copy(noteNaming = naming)
        }

        override fun setNoteDisplay(noteDisplay: NoteDisplay) {
            state.value = state.value.copy(noteDisplay = noteDisplay)
        }

        override fun setCountInBeats(beats: Int) {
            state.value = state.value.copy(countInBeats = beats)
        }

        override fun setCountInClick(enabled: Boolean) {
            state.value = state.value.copy(countInClick = enabled)
        }

        override fun setNoteDisplayFor(tool: TrainingTool, noteDisplay: NoteDisplay) {
            state.value = state.value.copy(
                noteDisplayOverrides = state.value.noteDisplayOverrides + (tool to noteDisplay),
            )
        }
    }

    private fun TestScope.viewModel(
        seed: Int = 1,
        preset: QuizPreset? = null,
    ) = QuizViewModel(
        settingsRepository = FakeSettingsRepository(),
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
    )

    private val c4 = Note(NoteName.C, 4)

    private fun asking(viewModel: QuizViewModel): QuizPhase.Asking {
        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is QuizPhase.Asking)
        return phase as QuizPhase.Asking
    }

    private fun answerCorrectly(viewModel: QuizViewModel) {
        viewModel.answer(asking(viewModel).question.correctIndex)
    }

    private fun answerWrongly(viewModel: QuizViewModel) {
        val question = asking(viewModel).question
        viewModel.answer((question.correctIndex + 1) % question.optionCount)
    }

    private fun result(viewModel: QuizViewModel): QuizResult {
        val phase = viewModel.uiState.value.phase
        assertTrue("phase was $phase", phase is QuizPhase.Finished)
        return (phase as QuizPhase.Finished).result
    }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the beginner set is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(NoteSet.BEGINNER, state.noteSet)
        assertEquals(8, state.activeNotes.size)
        assertEquals(QuizMode.NOTE_TO_FINGERING, state.mode)
        assertEquals(config.defaultQuestionCount, state.questionCount)
        assertTrue(state.canStart)
    }

    @Test
    fun `a quiz needs something to choose between`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.editCustomNotes()
        viewModel.clearCustomNotes()
        viewModel.toggleCustomNote(c4)
        viewModel.applyCustomNotes()

        assertFalse("one note is not a question", viewModel.uiState.value.canStart)

        viewModel.start()

        assertEquals(QuizPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the setup is not the player's to change once the quiz is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.selectMode(QuizMode.FINGERING_TO_NOTE)
        viewModel.selectQuestionCount(30)

        assertEquals(QuizMode.NOTE_TO_FINGERING, viewModel.uiState.value.mode)
        assertEquals(10, viewModel.uiState.value.questionCount)
    }

    // --- answering ---------------------------------------------------------------------------

    @Test
    fun `a right answer is kept and the next question comes up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        answerCorrectly(viewModel)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertTrue("the answer was right", answered.correct)
        assertEquals("and it is shown before moving on", 0, answered.index)

        advanceUntilIdle()

        assertEquals(1, asking(viewModel).index)
    }

    @Test
    fun `a wrong answer names the note it should have been`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        val question = asking(viewModel).question

        answerWrongly(viewModel)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertFalse(answered.correct)
        assertEquals(question.note, answered.question.note)

        advanceUntilIdle()
        viewModel.stop()

        assertEquals(listOf(question.note), result(viewModel).missedNotes)
    }

    @Test
    fun `a wrong answer is left up for longer than a right one`() = runTest(dispatcher) {
        val slow = viewModel()
        slow.start()
        answerWrongly(slow)
        advanceTimeBy((config.revealCorrectSeconds * 1_000).toLong() + 100)

        assertTrue(
            "a wrong answer needs longer to read",
            slow.uiState.value.phase is QuizPhase.Answered,
        )

        advanceUntilIdle()
        assertTrue(slow.uiState.value.phase is QuizPhase.Asking)
    }

    @Test
    fun `answering again skips the wait instead of counting twice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        answerCorrectly(viewModel)
        viewModel.answer(0)

        assertEquals(1, asking(viewModel).index)

        advanceUntilIdle()

        // The second tap moved it on; the waiting job must not move it on again.
        assertEquals(1, asking(viewModel).index)
    }

    @Test
    fun `an answer outside the options is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        val question = asking(viewModel).question

        viewModel.answer(question.optionCount)
        viewModel.answer(-1)

        assertTrue(viewModel.uiState.value.phase is QuizPhase.Asking)
    }

    // --- a whole run --------------------------------------------------------------------------

    @Test
    fun `the quiz ends after the questions it promised`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectQuestionCount(10)
        viewModel.start()

        repeat(10) {
            answerCorrectly(viewModel)
            advanceUntilIdle()
        }

        val result = result(viewModel)
        assertTrue(result.completed)
        assertEquals(10, result.answers.size)
        assertEquals(10, result.correctCount)
        assertTrue(result.missedNotes.isEmpty())
    }

    @Test
    fun `stopping early keeps the questions that were answered`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        answerCorrectly(viewModel)
        advanceUntilIdle()
        answerWrongly(viewModel)
        advanceUntilIdle()

        viewModel.stop()

        val result = result(viewModel)
        assertFalse(result.completed)
        assertEquals(2, result.answers.size)
        assertEquals(1, result.correctCount)
    }

    @Test
    fun `a note got wrong twice is only named once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectQuestionCount(10)
        viewModel.start()

        val missed = buildList {
            repeat(10) {
                add(asking(viewModel).question.note)
                answerWrongly(viewModel)
                advanceUntilIdle()
            }
        }

        val result = result(viewModel)
        assertEquals(missed.distinct().sortedBy { it.midiNumber }, result.missedNotes.sortedBy { it.midiNumber })
    }

    @Test
    fun `starting again begins a new run rather than continuing the last one`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start()
            answerCorrectly(viewModel)
            advanceUntilIdle()
            viewModel.stop()

            viewModel.start()

            assertEquals(0, asking(viewModel).index)

            viewModel.stop()
            assertEquals(0, result(viewModel).answers.size)
        }

    // --- ready-made quizzes -------------------------------------------------------------------

    @Test
    fun `a preset decides the whole quiz`() = runTest(dispatcher) {
        val notes = listOf(c4, Note(NoteName.D, 4), Note(NoteName.E, 4))
        val state = viewModel(
            preset = QuizPreset(
                notes = notes,
                mode = QuizMode.FINGERING_TO_NOTE,
                questionCount = 20,
            ),
        ).uiState.value

        assertEquals(notes, state.activeNotes)
        assertEquals(QuizMode.FINGERING_TO_NOTE, state.mode)
        assertEquals(20, state.questionCount)
        assertTrue(state.isPreconfigured)
        assertTrue(state.canStart)
    }

    @Test
    fun `a preset asks to start exactly once`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = QuizPreset(notes = listOf(c4, Note(NoteName.D, 4))))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse("rotation must not start it again", viewModel.consumeAutoStart())
    }

    @Test
    fun `a preset that does not ask to start waits`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = QuizPreset(notes = listOf(c4, Note(NoteName.D, 4)), autoStart = false),
        )

        assertFalse(viewModel.consumeAutoStart())
        assertEquals(QuizPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = runTest(dispatcher) {
        val state = viewModel(
            preset = QuizPreset(notes = listOf(c4, Note(NoteName.D, 4), Note(NoteName.C, 2))),
        ).uiState.value

        assertFalse(state.activeNotes.contains(Note(NoteName.C, 2)))
        assertEquals(2, state.activeNotes.size)
    }
}

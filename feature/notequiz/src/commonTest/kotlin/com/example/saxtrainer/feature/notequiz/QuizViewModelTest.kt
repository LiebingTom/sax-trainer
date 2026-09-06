package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Running a quiz: a question, an answer, the next question. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = QuizConfig()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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
        assertTrue(phase is QuizPhase.Asking, "phase was $phase")
        return phase
    }

    private fun answerCorrectly(viewModel: QuizViewModel) {
        asking(viewModel).question.answers.forEachIndexed { slot, answers ->
            viewModel.answer(slot, answers.correctIndex)
        }
    }

    /** The first slot wrong and the rest right: a question is wrong if any part of it is. */
    private fun answerWrongly(viewModel: QuizViewModel) {
        asking(viewModel).question.answers.forEachIndexed { slot, answers ->
            val option = if (slot == 0) {
                (answers.correctIndex + 1) % answers.optionCount
            } else {
                answers.correctIndex
            }
            viewModel.answer(slot, option)
        }
    }

    private fun result(viewModel: QuizViewModel): QuizResult {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is QuizPhase.Finished, "phase was $phase")
        return phase.result
    }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the beginner set is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(NoteSet.BEGINNER, state.noteSet)
        assertEquals(8, state.activeNotes.size)
        assertEquals(QuizMode.NOTE_AND_FINGERING, state.mode)
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

        assertFalse(viewModel.uiState.value.canStart, "one note is not a question")

        viewModel.start()

        assertEquals(QuizPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the setup is not the player's to change once the quiz is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.selectMode(QuizMode.FINGERING_TO_NOTE)
        viewModel.selectQuestionCount(30)

        assertEquals(QuizMode.NOTE_AND_FINGERING, viewModel.uiState.value.mode)
        assertEquals(10, viewModel.uiState.value.questionCount)
    }

    // --- answering ---------------------------------------------------------------------------

    @Test
    fun `a right answer is kept and the next question comes up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        answerCorrectly(viewModel)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertTrue(answered.correct, "the answer was right")
        assertEquals(0, answered.index, "and it is shown before moving on")

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
        advanceTimeBy((config.defaultSuccessDelaySeconds * 1_000).toLong() + 100)

        assertTrue(
            slow.uiState.value.phase is QuizPhase.Answered,
            "a wrong answer needs longer to read",
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

        viewModel.answer(question.answers.first().optionCount)
        viewModel.answer(-1)

        assertTrue(viewModel.uiState.value.phase is QuizPhase.Asking)
    }

    // --- both halves of a note ------------------------------------------------------------------

    /** The option that is not the right one, so a slot can be got deliberately wrong. */
    private fun wrongOptionFor(answers: QuizAnswers): Int =
        (answers.correctIndex + 1) % answers.optionCount

    @Test
    fun `a combined question is not over until both halves are answered`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        val question = asking(viewModel).question
        assertEquals(2, question.answers.size, "the default mode asks two things")

        viewModel.answer(slot = 0, option = question.answers[0].correctIndex)

        val half = asking(viewModel)
        assertFalse(half.isSettled, "one half answered is not an answered question")
        assertEquals(question.answers[0].correctIndex, half.chosen[0])
        assertNull(half.chosen[1], "the other half is still being asked")

        viewModel.answer(slot = 1, option = question.answers[1].correctIndex)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertTrue(answered.correct)
    }

    @Test
    fun `a half that has been answered keeps the answer it was given`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        val question = asking(viewModel).question
        val names = question.answers[0]

        viewModel.answer(slot = 0, option = names.correctIndex)
        viewModel.answer(slot = 0, option = wrongOptionFor(names))

        assertEquals(names.correctIndex, asking(viewModel).chosen[0], "what was chosen stands")

        viewModel.answer(slot = 1, option = question.answers[1].correctIndex)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertTrue(answered.correct, "the second tap on the first half changed nothing")
    }

    @Test
    fun `getting one half wrong still asks the other`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        val question = asking(viewModel).question

        viewModel.answer(slot = 0, option = wrongOptionFor(question.answers[0]))

        assertFalse(asking(viewModel).isSettled, "the grip is still worth asking for")

        viewModel.answer(slot = 1, option = question.answers[1].correctIndex)

        val answered = viewModel.uiState.value.phase as QuizPhase.Answered
        assertFalse(answered.correct, "half a note known is not the note known")

        advanceUntilIdle()
        viewModel.stop()

        assertEquals(listOf(question.note), result(viewModel).missedNotes)
    }

    @Test
    fun `the wait begins with the second half, not the first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSuccessDelaySeconds(2.0)
        viewModel.start()
        val question = asking(viewModel).question

        viewModel.answer(slot = 0, option = question.answers[0].correctIndex)
        advanceTimeBy(3_000)

        assertTrue(
            viewModel.uiState.value.phase is QuizPhase.Asking,
            "a half-answered question waits for the player, not for the clock",
        )
        assertEquals(0, asking(viewModel).index, "and it is still the same question")

        viewModel.answer(slot = 1, option = question.answers[1].correctIndex)

        assertTrue(viewModel.uiState.value.phase is QuizPhase.Answered)

        advanceTimeBy(2_100)

        assertEquals(1, asking(viewModel).index, "the wait began when the last half went in")
    }

    @Test
    fun `the wait can only be cut short once the whole question is right`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSuccessDelaySeconds(2.0)
        viewModel.start()
        val question = asking(viewModel).question

        viewModel.answer(slot = 0, option = question.answers[0].correctIndex)
        viewModel.skipSuccessDelay()

        assertEquals(0, asking(viewModel).index, "there is no wait yet to skip")

        viewModel.answer(slot = 1, option = question.answers[1].correctIndex)
        viewModel.skipSuccessDelay()

        assertEquals(1, asking(viewModel).index, "the rest of the wait was given up")
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
        assertEquals(
            missed.distinct().sortedBy { it.midiNumber },
            result.missedNotes.sortedBy { it.midiNumber },
        )
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
        assertFalse(viewModel.consumeAutoStart(), "rotation must not start it again")
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

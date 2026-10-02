package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.settings.AppSettings
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetup

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
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
        naming: NoteNaming = NoteNaming.INTERNATIONAL,
    ) = QuizViewModel(
        settingsRepository = FakeSettingsRepository(AppSettings(countInBeats = 0, noteNaming = naming)),
        exerciseSetups = setups,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
    )

    private fun spelled(name: NoteName, octave: Int) = SpelledNote.of(Note(name, octave))

    private fun written(token: String) = SpelledNote.parse(token)!!

    private val c4 = spelled(NoteName.C, 4)

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
    fun `no note is asked about twice running`() = runTest(dispatcher) {
        repeat(10) { seed ->
            val viewModel = viewModel(seed = seed)
            viewModel.selectQuestionCount(10)
            viewModel.start()

            val asked = buildList {
                repeat(10) {
                    add(asking(viewModel).question.note)
                    answerCorrectly(viewModel)
                    advanceUntilIdle()
                }
            }

            asked.zipWithNext().forEach { (first, second) ->
                assertTrue(first.note != second.note, "seed $seed asked $first twice running")
            }
        }
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
            missed.distinct().sortedWith(SpelledNote.PITCH_ORDER),
            result.missedNotes.sortedWith(SpelledNote.PITCH_ORDER),
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
        val notes = listOf(c4, spelled(NoteName.D, 4), spelled(NoteName.E, 4))
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

    // --- written spelling ------------------------------------------------------------------

    @Test
    fun `a preset is asked exactly as it is written, in either naming`() = runTest(dispatcher) {
        val notes = listOf("C4", "D4", "Eb4", "F4", "G4", "Ab4", "Bb4").map(::written)
        val preset = QuizPreset(notes = notes, autoStart = false)

        val international = viewModel(preset = preset).uiState.value
        val german = viewModel(preset = preset, naming = NoteNaming.GERMAN).uiState.value

        assertEquals(notes, international.activeNotes)
        assertEquals(listOf("C4", "D4", "E♭4", "F4", "G4", "A♭4", "B♭4"), notes.map(international::nameOf))
        assertEquals(listOf("C4", "D4", "Es4", "F4", "G4", "As4", "B4"), notes.map(german::nameOf))
    }

    @Test
    fun `one pitch in two spellings is read the same way everywhere a quiz is decided`() =
        runTest(dispatcher) {
            // Chosen by hand: kept as chosen, but not something a question can be asked about.
            val setups = InMemoryExerciseSetupRepository()
            val viewModel = viewModel(setups = setups)
            viewModel.selectOnly(written("D#4"), written("Eb4"))

            val state = viewModel.uiState.value
            assertEquals(listOf(written("D#4"), written("Eb4")), state.activeNotes)
            assertFalse(state.canStart, "the one cannot be a wrong answer for the other")
            viewModel.start()
            assertEquals(QuizPhase.Idle, viewModel.uiState.value.phase, "so Start does nothing")

            // Saved, it is no quiz either: a lesson made of it is passed over rather than broken.
            assertNull(quizPresetOf(setups.setupOf(Exercise.NOTE_QUIZ)))

            // And a ready-made quiz cannot be built of it at all - the same rule, said by the type.
            assertFailsWith<IllegalArgumentException> {
                QuizPreset(notes = listOf(written("D#4"), written("Eb4")))
            }
            // One more pitch makes it a quiz, both spellings kept.
            val three = QuizPreset(notes = listOf(written("D#4"), written("Eb4"), written("F4")))
            assertTrue(viewModel(preset = three).uiState.value.canStart)
        }

    @Test
    fun `a spelling chosen by hand survives saving and starting`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()
        val chosen = listOf("C4", "Eb4", "E#4", "Fb4", "Ab4", "B#4", "Cb5").map(::written)
        viewModel(setups = setups).selectOnly(*chosen.toTypedArray())

        val reopened = viewModel(setups = setups).uiState.value
        val started = assertNotNull(quizPresetOf(setups.setupOf(Exercise.NOTE_QUIZ)))

        assertEquals(chosen.sortedWith(SpelledNote.PITCH_ORDER), reopened.activeNotes)
        assertEquals(chosen.sortedWith(SpelledNote.PITCH_ORDER), started.notes)
    }

    @Test
    fun `a setup saved before spellings were kept opens with the usual spellings`() =
        runTest(dispatcher) {
            val old = com.example.saxtrainer.core.settings.exerciseSetup {
                put("note_set", "CUSTOM")
                putInts("custom_notes", listOf(61, 63, 70))
            }

            val preset = assertNotNull(quizPresetOf(old))

            assertEquals(listOf("C#4", "D#4", "Bb4"), preset.notes.map { it.token })
        }

    @Test
    fun `a preset asks to start exactly once`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = QuizPreset(notes = listOf(c4, spelled(NoteName.D, 4))))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse(viewModel.consumeAutoStart(), "rotation must not start it again")
    }

    @Test
    fun `a preset that does not ask to start waits`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = QuizPreset(notes = listOf(c4, spelled(NoteName.D, 4)), autoStart = false),
        )

        assertFalse(viewModel.consumeAutoStart())
        assertEquals(QuizPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = runTest(dispatcher) {
        val state = viewModel(
            preset = QuizPreset(notes = listOf(c4, spelled(NoteName.D, 4), spelled(NoteName.C, 2))),
        ).uiState.value

        assertFalse(state.activeNotes.contains(spelled(NoteName.C, 2)))
        assertEquals(2, state.activeNotes.size)
    }

    // --- the round trip ---------------------------------------------------------------------

    /** Puts exactly these notes into the custom set and switches to it, as the dialog would. */
    private fun QuizViewModel.selectOnly(vararg notes: SpelledNote) {
        editCustomNotes()
        clearCustomNotes()
        notes.forEach(::toggleCustomNote)
        applyCustomNotes()
    }

    /**
     * Every choice the setup screen offers survives being written down, turned back into a
     * ready-made quiz, and written down again.
     *
     * A stored setup has two ways out - one opens the setup screen on it, the other starts it - and
     * they have to agree. Where they do not, the difference shows only in a quiz somebody saved,
     * and only once it is started, which is the worst place to find it.
     */
    @Test
    fun `every choice survives being written down and started again`() = runTest(dispatcher) {
        val configured = viewModel()
        configured.selectOnly(
            c4,
            spelled(NoteName.D, 4),
            spelled(NoteName.E, 4),
            spelled(NoteName.F, 4),
        )
        configured.selectMode(QuizMode.FINGERING_TO_NOTE)
        configured.selectQuestionCount(15)
        configured.selectSuccessDelaySeconds(2.0)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(quizPresetOf(written, editable = true))
        val started = viewModel(preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `too few notes to choose between is not a quiz that can be started`() =
        runTest(dispatcher) {
            val configured = viewModel()
            configured.selectOnly(c4)

            assertNull(quizPresetOf(configured.uiState.value.toSetup()))
        }


    /**
     * A practice nobody has touched is still a practice.
     *
     * The setup is written down as it is changed, so an exercise opened and left on its defaults
     * has nothing on record - and "add to your lessons" would have found nothing to save. Asking
     * for it writes it.
     */
    @Test
    fun `an untouched setup is written down when it is asked for`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.NOTE_QUIZ).isEmpty)
    }


    /**
     * A setup nobody has written yet is still a practice.
     *
     * Nothing is on record until a control is changed, so an exercise saved straight off its
     * defaults arrives here as an empty setup - and reading that as "no notes chosen" would save a
     * lesson that then quietly refuses to start.
     */
    @Test
    fun `an empty setup is the beginner's notes rather than none at all`() {
        val preset = assertNotNull(quizPresetOf(ExerciseSetup()))

        assertTrue(preset.notes.size >= QuizQuestionGenerator.MIN_OPTIONS)
    }

}

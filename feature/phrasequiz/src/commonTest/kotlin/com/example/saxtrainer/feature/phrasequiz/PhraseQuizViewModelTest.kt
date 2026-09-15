package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Running a phrase quiz: a unit shown, hidden, rebuilt in order, the next unit. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhraseQuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = PhraseQuizConfig(defaultShowSeconds = 1.0, revealSeconds = 1.0)

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
        preset: PhraseQuizPreset? = null,
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    ) = PhraseQuizViewModel(
        settingsRepository = FakeSettingsRepository(),
        exerciseSetups = setups,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
    )

    private fun asking(viewModel: PhraseQuizViewModel): PhraseQuizPhase.Asking {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is PhraseQuizPhase.Asking, "phase was $phase")
        return phase
    }

    /**
     * Taps the options back into the order [PhraseQuizPhase.Asking.correct] asks for.
     *
     * Re-reads the phase before every tap rather than once: [PhraseQuizPhase.Asking.chosen] grows
     * with each one, and a stale copy would offer to place the same option twice.
     */
    private fun answerCorrectly(viewModel: PhraseQuizViewModel) {
        val correct = asking(viewModel).correct
        correct.forEach { step ->
            val current = asking(viewModel)
            val option = current.options.withIndex()
                .first { (index, option) -> option.note == step.note && index !in current.chosen }
                .index
            viewModel.choose(option)
        }
    }

    /** Taps the options back in the reverse of [PhraseQuizPhase.Asking.correct]'s order. */
    private fun answerReversed(viewModel: PhraseQuizViewModel) {
        val correct = asking(viewModel).correct
        correct.reversed().forEach { step ->
            val current = asking(viewModel)
            val option = current.options.withIndex()
                .first { (index, option) -> option.note == step.note && index !in current.chosen }
                .index
            viewModel.choose(option)
        }
    }

    private fun result(viewModel: PhraseQuizViewModel): PhraseQuizResult {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is PhraseQuizPhase.Finished, "phase was $phase")
        return phase.result
    }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the library's first phrase is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(PhraseSourceType.LIBRARY, state.sourceType)
        assertEquals(PhraseLibrary.all.first().id, state.libraryPhraseId)
        assertTrue(state.canStart)
    }

    @Test
    fun `opening a picker does not change the source until something is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.openPatternPicker()

        val state = viewModel.uiState.value
        assertTrue(state.patternPickerOpen)
        assertEquals(PhraseSourceType.LIBRARY, state.sourceType, "still the library, until a pattern is picked")
    }

    @Test
    fun `picking a pattern selects it and closes the window`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.openPatternPicker()

        viewModel.selectPattern(PatternLibrary.all.first().id)

        val state = viewModel.uiState.value
        assertEquals(PhraseSourceType.SCALE_PATTERN, state.sourceType)
        assertFalse(state.patternPickerOpen)
    }

    @Test
    fun `picking a library phrase selects it and closes the window`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice(ScaleChoice.defaults[1].id) // start from scale pattern
        viewModel.selectPattern(PatternLibrary.all.first().id)
        viewModel.openLibraryPicker()

        viewModel.selectLibraryPhrase(PhraseLibrary.all.first().id)

        val state = viewModel.uiState.value
        assertEquals(PhraseSourceType.LIBRARY, state.sourceType)
        assertFalse(state.libraryPickerOpen)
    }

    /**
     * The same pattern resolves differently in different keys: D major's "1-2-3-5" pattern is
     * D-E-F#-A, and the same pattern against C major is C-D-E-G, because the *degrees* are the
     * same but the *scale* is not.
     */
    @Test
    fun `a stored pattern is resolved against the chosen scale and octave`() = runTest(dispatcher) {
        val pattern = PatternLibrary.byId("one_two_three_five")!!

        val dMajor = viewModel()
        dMajor.selectScaleChoice("d_major")
        dMajor.selectPattern(pattern.id)
        assertEquals(
            pattern.resolve(ScaleChoice.byId("d_major")!!.scale, com.example.saxtrainer.core.music.Note(com.example.saxtrainer.core.music.NoteName.D, PhraseQuizUiState.DEFAULT_OCTAVE)).toneSequence().notes,
            dMajor.uiState.value.phrase?.toneSequence()?.notes,
        )

        val cMajor = viewModel()
        cMajor.selectScaleChoice("c_major")
        cMajor.selectPattern(pattern.id)
        assertEquals(
            pattern.resolve(ScaleChoice.byId("c_major")!!.scale, com.example.saxtrainer.core.music.Note(com.example.saxtrainer.core.music.NoteName.C, PhraseQuizUiState.DEFAULT_OCTAVE)).toneSequence().notes,
            cMajor.uiState.value.phrase?.toneSequence()?.notes,
        )
    }

    @Test
    fun `the octaves are their own setting`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern("triad_up")
        viewModel.toggleOctave(PhraseQuizUiState.DEFAULT_OCTAVE)
        viewModel.toggleOctave(5)

        assertEquals(setOf(5), viewModel.uiState.value.octaves)
        assertEquals(5, viewModel.uiState.value.phrase?.toneSequence()?.notes?.first()?.octave)
    }

    @Test
    fun `several octaves generate one phrase out of each octave's own bars, lowest first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern("triad_up")
        viewModel.toggleOctave(5) // default octave 4 is already on

        val phrase = assertNotNull(viewModel.uiState.value.phrase, "expected a resolved phrase")
        val octaves = phrase.toneSequence().notes.map { it.octave }
        assertEquals(octaves, octaves.sorted(), "octave 4's own notes come before octave 5's")
        assertEquals(setOf(4, 5), octaves.toSet())
    }

    @Test
    fun `turning off every octave leaves a scale pattern with nothing to resolve`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern("triad_up")
        viewModel.toggleOctave(PhraseQuizUiState.DEFAULT_OCTAVE)

        assertTrue(viewModel.uiState.value.octaves.isEmpty())
        assertFalse(viewModel.uiState.value.canStart)
    }

    @Test
    fun `the setup is not the player's to change once the quiz is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.selectScaleChoice(ScaleChoice.defaults[1].id)

        assertEquals(PhraseSourceType.LIBRARY, viewModel.uiState.value.sourceType)
    }

    // --- rebuilding a unit --------------------------------------------------------------------

    @Test
    fun `a unit is shown before it can be answered`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Showing)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Asking)
    }

    @Test
    fun `a right order is kept and the next unit comes up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        answerCorrectly(viewModel)

        val answered = viewModel.uiState.value.phase as PhraseQuizPhase.Answered
        assertTrue(answered.correctOrder)

        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.phase is PhraseQuizPhase.Asking ||
                viewModel.uiState.value.phase is PhraseQuizPhase.Finished,
        )
    }

    @Test
    fun `tapping the same option twice does not place it twice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.choose(0)
        viewModel.choose(0)

        assertEquals(listOf(0), asking(viewModel).chosen)
    }

    @Test
    fun `the option grid is padded out with decoys, not just the unit's own notes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        val phase = asking(viewModel)

        assertTrue(phase.options.size > phase.correct.size, "expected decoys alongside ${phase.correct.size} real notes")
        assertEquals(config.optionGridSize, phase.options.size)

        // Every one of the unit's own notes is still an option exactly as many times as it appears -
        // the decoys pad the grid, they never replace a real note.
        val optionNotes = phase.options.map { it.note }
        phase.correct.map { it.note }.groupingBy { it }.eachCount().forEach { (note, count) ->
            assertTrue(optionNotes.count { it == note } >= count, "the real note $note must stay an option")
        }
    }

    @Test
    fun `an option outside range is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        val optionCount = asking(viewModel).options.size

        viewModel.choose(-1)
        viewModel.choose(optionCount)

        assertTrue(asking(viewModel).chosen.isEmpty())
    }

    @Test
    fun `a wrong order is judged wrong`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        val correct = asking(viewModel).correct

        // Placing every note back to front is wrong unless the unit happens to be a palindrome.
        answerReversed(viewModel)

        val answered = viewModel.uiState.value.phase
        assertTrue(answered is PhraseQuizPhase.Answered)
        if (correct.map { it.note } != correct.map { it.note }.reversed()) {
            assertFalse(answered.correctOrder)
        }
    }

    // --- a whole run --------------------------------------------------------------------------

    @Test
    fun `the quiz ends after every unit of the phrase`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val unitCount = viewModel.uiState.value.units.size
        viewModel.start()

        repeat(unitCount) {
            advanceUntilIdle()
            answerCorrectly(viewModel)
        }
        advanceUntilIdle()

        val finished = result(viewModel)
        assertTrue(finished.completed)
        assertEquals(unitCount, finished.attempted.size)
        assertEquals(unitCount, finished.correctCount)
    }

    @Test
    fun `stopping early keeps the units that were attempted`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        answerCorrectly(viewModel)
        advanceUntilIdle()
        viewModel.stop()

        val finished = result(viewModel)
        assertFalse(finished.completed)
        assertEquals(1, finished.attempted.size)
    }

    // --- ready-made quizzes -------------------------------------------------------------------

    @Test
    fun `a preset decides the whole quiz`() = runTest(dispatcher) {
        val state = viewModel(
            preset = PhraseQuizPreset(source = PhraseSource.Library(PhraseLibrary.all.first().id)),
        ).uiState.value

        assertEquals(PhraseLibrary.all.first(), state.phrase)
        assertTrue(state.isPreconfigured)
        assertTrue(state.canStart)
    }

    @Test
    fun `a preset asks to start exactly once`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = PhraseQuizPreset(source = PhraseSource.Library(PhraseLibrary.all.first().id)),
        )

        assertTrue(viewModel.consumeAutoStart())
        assertFalse(viewModel.consumeAutoStart(), "rotation must not start it again")
    }

    // --- the round trip ---------------------------------------------------------------------

    @Test
    fun `every choice survives being written down and started again`() = runTest(dispatcher) {
        val configured = viewModel()
        configured.selectScaleChoice(ScaleChoice.defaults[2].id)
        configured.selectPattern(PatternLibrary.all.last().id)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(phraseQuizPresetOf(written, editable = true))
        val started = viewModel(preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `an untouched setup is written down when it is asked for`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.PHRASE_QUIZ).isEmpty)
    }
}

package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.phrase.InMemoryPhraseCatalog
import com.example.saxtrainer.core.phrase.parsePhrases
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.testing.TestPatterns
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
    private val config = PhraseQuizConfig(defaultShowSeconds = 1.0)

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
        phrases: com.example.saxtrainer.core.phrase.PhraseCatalog = PhraseLibrary,
    ) = PhraseQuizViewModel(
        settingsRepository = FakeSettingsRepository(),
        exerciseSetups = setups,
        phraseCatalog = phrases,
        patternCatalog = TestPatterns.catalog,
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

        viewModel.selectPattern(TestPatterns.catalog.all.first().id)

        val state = viewModel.uiState.value
        assertEquals(PhraseSourceType.SCALE_PATTERN, state.sourceType)
        assertFalse(state.patternPickerOpen)
    }

    @Test
    fun `picking a library phrase selects it and closes the window`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("d_major") // start from scale pattern
        viewModel.selectPattern(TestPatterns.catalog.all.first().id)
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
        val pattern = TestPatterns.walk

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
        viewModel.selectPattern(TestPatterns.WALK_ID)
        viewModel.toggleOctave(PhraseQuizUiState.DEFAULT_OCTAVE)
        viewModel.toggleOctave(5)

        assertEquals(setOf(5), viewModel.uiState.value.octaves)
        assertEquals(5, viewModel.uiState.value.phrase?.toneSequence()?.notes?.first()?.octave)
    }

    @Test
    fun `several octaves generate one phrase out of each octave's own bars, lowest first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID)
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
        viewModel.selectPattern(TestPatterns.WALK_ID)
        viewModel.toggleOctave(PhraseQuizUiState.DEFAULT_OCTAVE)

        assertTrue(viewModel.uiState.value.octaves.isEmpty())
        assertFalse(viewModel.uiState.value.canStart)
    }

    @Test
    fun `an out-of-range preview time is clamped to the largest timed step, not to infinite`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val largestTimed = PhraseQuizUiState.SHOW_SECONDS_OPTIONS[PhraseQuizUiState.SHOW_SECONDS_OPTIONS.lastIndex - 1]

        viewModel.setShowSeconds(100.0)
        assertEquals(largestTimed, viewModel.uiState.value.showSeconds)

        viewModel.setShowSeconds(-1.0)
        assertEquals(PhraseQuizUiState.SHOW_SECONDS_OPTIONS.first(), viewModel.uiState.value.showSeconds)
    }

    @Test
    fun `infinite preview time is only reached by asking for it exactly`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setShowSeconds(Double.POSITIVE_INFINITY)

        assertEquals(Double.POSITIVE_INFINITY, viewModel.uiState.value.showSeconds)
    }

    @Test
    fun `the setup is not the player's to change once the quiz is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.selectScaleChoice("d_major")

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
    fun `revealNow cuts a timed preview short`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Showing)

        viewModel.revealNow()

        // No advanceUntilIdle: the point is that this needed no time to pass at all.
        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Asking)
    }

    @Test
    fun `an infinite preview waits for revealNow rather than a timer`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setShowSeconds(Double.POSITIVE_INFINITY)

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Showing, "nothing times it out on its own")

        viewModel.revealNow()

        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Asking)
    }

    @Test
    fun `revealNow does nothing outside the showing phase`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.revealNow()

        assertEquals(PhraseQuizPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a right order is kept and the next unit comes up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        answerCorrectly(viewModel)

        val answered = viewModel.uiState.value.phase as PhraseQuizPhase.Answered
        assertTrue(answered.correctOrder)

        // Nothing moves on by itself - the player has to say so.
        assertTrue(viewModel.uiState.value.phase is PhraseQuizPhase.Answered)
        viewModel.continueToNextUnit()
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
    fun `undo frees the last note placed, not the whole order`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.choose(0)
        viewModel.choose(1)
        viewModel.undoLast()

        assertEquals(listOf(0), asking(viewModel).chosen, "only the last tile placed comes back")

        // A freed tile is a tile with nowhere it counts as "used": it can be chosen again.
        viewModel.choose(1)
        assertEquals(listOf(0, 1), asking(viewModel).chosen)
    }

    @Test
    fun `undo does nothing before anything has been placed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.undoLast()

        assertTrue(asking(viewModel).chosen.isEmpty())
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
            viewModel.continueToNextUnit()
        }
        advanceUntilIdle()

        val finished = result(viewModel)
        assertTrue(finished.completed)
        assertEquals(unitCount, finished.attempted.size)
        assertEquals(unitCount, finished.correctCount)
    }

    @Test
    fun `repeats walk the whole phrase more than once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val unitCount = viewModel.uiState.value.units.size
        viewModel.selectRepeats(3)
        assertEquals(unitCount * 3, viewModel.uiState.value.plannedUnitCount)

        viewModel.start()
        repeat(unitCount * 3) {
            advanceUntilIdle()
            answerCorrectly(viewModel)
            viewModel.continueToNextUnit()
        }
        advanceUntilIdle()

        val finished = result(viewModel)
        assertTrue(finished.completed)
        assertEquals(unitCount * 3, finished.attempted.size)
    }

    @Test
    fun `mixed order draws every repeat from one shuffled pool`() = runTest(dispatcher) {
        val viewModel = viewModel(seed = 7)
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID) // eight units, so a run of three repeats has twenty-four
        val phrase = assertNotNull(viewModel.uiState.value.phrase)
        val unitCount = phrase.units.size
        viewModel.selectRepeats(3)
        viewModel.setMixed(true)

        viewModel.start()
        val order = mutableListOf<List<com.example.saxtrainer.core.music.Note?>>()
        repeat(unitCount * 3) {
            advanceUntilIdle()
            order += asking(viewModel).correct.map { it.note }
            answerCorrectly(viewModel)
            viewModel.continueToNextUnit()
        }
        advanceUntilIdle()

        // Every unit appears exactly [repeats] times in the pool, whatever order they came in - two
        // of the walk's own bars happen to read the same, so counted by notes rather than by
        // position, that one reading is expected twice as often as any other.
        assertEquals(unitCount * 3, order.size)
        val expectedCounts = phrase.units
            .map { unit -> phrase.toneSequenceOf(unit).steps.map { it.note } }
            .groupingBy { it }
            .eachCount()
            .mapValues { (_, occurrences) -> occurrences * 3 }
        assertEquals(expectedCounts, order.groupingBy { it }.eachCount())
        assertTrue(result(viewModel).completed)
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
        configured.selectScaleChoice("g_major")
        configured.selectPattern(TestPatterns.catalog.all.last().id)
        configured.selectRepeats(4)
        configured.setMixed(true)
        configured.setShowSeconds(4.0)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(phraseQuizPresetOf(written, patterns = TestPatterns.catalog, editable = true))
        val started = viewModel(preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `an untouched setup is written down when it is asked for`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.PHRASE_QUIZ).isEmpty)
    }

    // --- written spelling ---------------------------------------------------------------------

    /**
     * One bar written with flats in a phrase whose own fallback style is sharps: what the file
     * writes has to win, note by note.
     */
    private val flatPhrase = InMemoryPhraseCatalog(
        parsePhrases(
            listOf(
                """
                {"id": "flats", "name": "Flats", "accidentalStyle": "sharps", "bars": [ { "steps": [
                  { "note": "C4", "duration": "quarter" }, { "note": "Eb4", "duration": "quarter" },
                  { "note": "G4", "duration": "quarter" }, { "note": "Bb4", "duration": "quarter" }
                ] } ] }
                """.trimIndent(),
            ),
        ),
    )

    @Test
    fun `a phrase's notes are shown, offered and put back exactly as written`() = runTest(dispatcher) {
        val viewModel = viewModel(phrases = flatPhrase)
        viewModel.start()
        val shown = viewModel.uiState.value.phase as PhraseQuizPhase.Showing
        assertEquals(listOf("C4", "Eb4", "G4", "Bb4"), shown.correct.map { it.written?.token })

        viewModel.revealNow()
        val asking = asking(viewModel)
        val correctPitches = asking.correct.mapNotNull { it.note }
        val decoys = asking.options.filterNot { option -> asking.correct.any { it.written == option.written } }
        assertTrue(
            decoys.none { it.note in correctPitches },
            "a decoy respells a note of the phrase: ${decoys.map { it.written }}",
        )
        assertTrue(asking.options.all { it.written != null }, "every tile knows how it is written")

        answerCorrectly(viewModel)
        val answered = viewModel.uiState.value.phase as PhraseQuizPhase.Answered
        assertTrue(answered.correctOrder)
        assertEquals(
            listOf("C4", "Eb4", "G4", "Bb4"),
            answered.builtNotes.map { it?.token },
            "built back as written, not as D sharp and A sharp",
        )
    }
}

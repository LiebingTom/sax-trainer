package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.phrase.InMemoryPhraseCatalog
import com.example.saxtrainer.core.phrase.PhraseCatalog
import com.example.saxtrainer.core.phrase.parsePhrases
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.TestPatterns
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Showing a unit, hiding it, and playing it back into a microphone. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhraseTrainerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * A test of this trainer, on virtual time and with a deadline - see the note trainer's own
     * `trainerTest` for why: the fake microphone emits for ever, so a run that never ends would
     * hang virtual time rather than fail the test.
     */
    private fun trainerTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher, timeout = DEADLINE, testBody = body)

    // A short show and a short hold, so a test does not spend virtual time on either.
    private val config = PhraseTrainerConfig(defaultShowSeconds = 0.1, requiredCorrectSeconds = 0.1)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        microphone: AudioCapture? = FakeMicrophone(FRAME_DURATION_MS),
        // Concert pitch, not the app's own default (tenor sax): so a written note and the frequency
        // this test plays into the fake microphone are the same number, with nothing to transpose.
        settings: SettingsRepository = FakeSettingsRepository(
            AppSettings(countInBeats = 0, instrument = Instrument.CONCERT),
        ),
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
        preset: PhraseTrainerPreset? = null,
        random: Random = Random.Default,
        phrases: PhraseCatalog = PhraseLibrary,
    ) = PhraseTrainerViewModel(
        settingsRepository = settings,
        exerciseSetups = setups,
        audioCapture = microphone,
        phraseCatalog = phrases,
        patternCatalog = TestPatterns.catalog,
        preset = preset,
        config = config,
        random = random,
        processingDispatcher = dispatcher,
        countInClock = MonotonicBeatClock(nanoTime = { testScheduler.currentTime * 1_000_000 }),
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    /** What a written note sounds like at concert pitch - the default instrument in these tests. */
    private fun soundingFrequencyOf(written: Note) = written.frequencyHz()

    /** The library's default phrase, and its own first practice unit - two bars, eight notes. */
    private val defaultPhrase = PhraseLibrary.all.first()
    private val firstUnitNotes = defaultPhrase.toneSequenceOf(defaultPhrase.units.first()).steps.mapNotNull { it.note }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the library's first phrase is ready to go on the first run`() = trainerTest {
        val state = viewModel().uiState.value

        assertEquals(PhraseLibrary.all.first().id, state.libraryPhraseId)
        assertTrue(state.canStart)
    }

    @Test
    fun `picking a pattern selects it and closes the window`() = trainerTest {
        val viewModel = viewModel()
        viewModel.openPatternPicker()

        viewModel.selectPattern(TestPatterns.catalog.all.first().id)

        val state = viewModel.uiState.value
        assertTrue(state.canStart)
        assertFalse(state.patternPickerOpen)
    }

    /**
     * The same pattern resolves differently in different keys: D major's "1-2-3-5" pattern is
     * D-E-F#-A, and the same pattern against C major is C-D-E-G, because the *degrees* are the
     * same but the *scale* is not.
     */
    @Test
    fun `a stored pattern is resolved against the chosen scale and octave`() = trainerTest {
        val dMajor = viewModel()
        dMajor.selectScaleChoice("d_major")
        dMajor.selectPattern(TestPatterns.WALK_ID)
        // The pattern's own first bar - degrees 1, 2, 3, 5.
        assertEquals(
            listOf(
                Note(NoteName.D, 4),
                Note(NoteName.E, 4),
                Note(NoteName.F_SHARP, 4),
                Note(NoteName.A, 4),
            ),
            dMajor.uiState.value.phrase?.toneSequence()?.notes?.take(4),
        )

        val cMajor = viewModel()
        cMajor.selectScaleChoice("c_major")
        cMajor.selectPattern(TestPatterns.WALK_ID)
        assertEquals(
            listOf(Note(NoteName.C, 4), Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.G, 4)),
            cMajor.uiState.value.phrase?.toneSequence()?.notes?.take(4),
        )
    }

    @Test
    fun `the octaves are their own setting`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID)
        viewModel.toggleOctave(PhraseTrainerUiState.DEFAULT_OCTAVE)
        viewModel.toggleOctave(5)

        assertEquals(setOf(5), viewModel.uiState.value.octaves)
        assertEquals(5, viewModel.uiState.value.phrase?.toneSequence()?.notes?.first()?.octave)
    }

    @Test
    fun `no microphone means no exercise`() = trainerTest {
        val state = viewModel(microphone = null).uiState.value
        assertTrue(state.canStart, "the setup itself is still fine")

        val viewModel = viewModel(microphone = null)
        viewModel.start()

        assertEquals(PhraseTrainerPhase.Idle, viewModel.uiState.value.phase)
    }

    // --- playing --------------------------------------------------------------------------

    @Test
    fun `a unit is shown before it can be played`() = trainerTest {
        val viewModel = viewModel()
        viewModel.start()

        // Still counting in and then showing; nothing has been asked of the microphone yet.
        advanceTimeBy(4_000)

        // Captured before stopping: the ViewModel's own coroutine is not a child of this test's
        // scope, and an assertion failing while it still runs would leave it spinning forever on
        // the same virtual clock this test is about to hand back.
        val phase = viewModel.uiState.value.phase
        viewModel.stop()

        assertTrue(phase is PhraseTrainerPhase.Showing || phase is PhraseTrainerPhase.Listening)
    }

    @Test
    fun `playing every note of a unit correctly moves on to the next one`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone)
        viewModel.start()

        // Past the count-in and the brief showing of the first unit.
        advanceTimeBy(5_000)

        firstUnitNotes.forEach { note ->
            microphone.frequencyHz = soundingFrequencyOf(note)
            advanceTimeBy(700)
        }

        // Stopped before asserting: viewModelScope's coroutine is not a child of this test's
        // scope, and an assertion failing before it is cancelled would leave it running forever
        // on the same virtual clock this test is about to hand back.
        val progress = viewModel.uiState.value.progress
        viewModel.stop()

        if (progress != null) {
            assertTrue(progress.unitIndex >= 1, "the first unit's notes were all played")
        }
    }

    @Test
    fun `one stray frame while playing a note does not restart its hold`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone)
        viewModel.start()
        advanceTimeBy(5_000) // past the count-in and the brief showing of the first unit

        val expected = firstUnitNotes.first()
        val decoy = Note(NoteName.C, 6) // far from anything in the phrase, so unmistakably wrong

        microphone.frequencyHz = soundingFrequencyOf(expected)
        advanceTimeBy(FRAME_DURATION_MS * 2)
        // One glitch frame - a reed squeak, a harmonic the detector snapped onto for an instant -
        // not a real change of note. What is left afterwards on its own is under the 0.1 s this
        // config requires; only added to what came before the glitch does it clear that.
        microphone.frequencyHz = soundingFrequencyOf(decoy)
        advanceTimeBy(FRAME_DURATION_MS)
        microphone.frequencyHz = soundingFrequencyOf(expected)
        advanceTimeBy(FRAME_DURATION_MS + 500)

        val progress = viewModel.uiState.value.progress
        viewModel.stop()

        if (progress != null) {
            assertTrue(progress.stepIndex >= 1, "the note landed despite one stray frame, progress was $progress")
        }
    }

    @Test
    fun `skip gives up on a note nobody can find`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone)
        viewModel.start()
        advanceTimeBy(5_000)
        val stepBeforeSkip = viewModel.uiState.value.progress?.stepIndex

        viewModel.skip()
        advanceTimeBy(200)

        // Captured before stopping - see `a unit is shown before it can be played` for why.
        val phaseAfterSkip = viewModel.uiState.value.phase
        val stepAfterSkip = viewModel.uiState.value.progress?.stepIndex
        viewModel.stop()

        assertTrue(phaseAfterSkip is PhraseTrainerPhase.Listening, "still on to the next note")
        val before = assertNotNull(stepBeforeSkip)
        val after = assertNotNull(stepAfterSkip)
        assertTrue(after > before, "skip moved on rather than waiting for ever")
    }

    @Test
    fun `repeats walk the whole phrase more than once`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID) // eight bars, one unit each
        viewModel.selectRepeats(2)

        val root = assertNotNull(viewModel.uiState.value.scaleChoice)
        val resolved = assertNotNull(viewModel.uiState.value.pattern)
            .resolve(root.scale, root.root, setOf(PhraseTrainerUiState.DEFAULT_OCTAVE))
        val unitCount = resolved.units.size
        assertEquals(unitCount * 2, viewModel.uiState.value.plannedUnitCount)

        viewModel.start()
        advanceTimeBy(5_000)

        repeat(2) {
            resolved.units.forEach { unit ->
                resolved.toneSequenceOf(unit).steps.mapNotNull { it.note }.forEach { note ->
                    microphone.frequencyHz = soundingFrequencyOf(note)
                    advanceTimeBy(700)
                }
                advanceTimeBy(700) // the moment on the landed note, and the next unit's own showing
            }
        }
        advanceTimeBy(2_000)

        val phase = viewModel.uiState.value.phase
        viewModel.stop()

        assertTrue(phase is PhraseTrainerPhase.Finished, "phase was $phase")
        assertEquals(unitCount * 2, phase.result.attempts.size)
        assertTrue(phase.result.completed)
    }

    @Test
    fun `every round of a repeated run has a unit to show`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID)
        viewModel.selectRepeats(3)

        viewModel.start()
        val state = viewModel.uiState.value
        viewModel.stop()

        // The screen looks a shown or played unit up by its index in the run, and the run is three
        // passes long: every index of every round has to find one, not only the first pass's.
        assertEquals(state.units.size * 3, state.runUnits.size)
        assertEquals(state.units + state.units + state.units, state.runUnits)
    }

    @Test
    fun `a mixed run shows the unit that is being played`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone, random = Random(7))
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern(TestPatterns.WALK_ID)
        viewModel.selectRepeats(2)
        viewModel.setMixed(true)

        viewModel.start()
        advanceTimeBy(5_000) // past the count-in and the first unit's showing

        val state = viewModel.uiState.value
        viewModel.stop()

        assertEquals(state.units.size * 2, state.runUnits.size)
        assertEquals(
            (state.units + state.units).groupingBy { it }.eachCount(),
            state.runUnits.groupingBy { it }.eachCount(),
            "a mixed run is the same units in another order",
        )
        val progress = assertNotNull(state.progress)
        val phrase = assertNotNull(state.phrase)
        val shown = phrase.toneSequenceOf(state.runUnits[progress.unitIndex]).steps.mapNotNull { it.note }
        assertEquals(shown.first(), progress.currentNote?.note, "the note asked for is the shown unit's")
    }

    @Test
    fun `the preview time is clamped to the offered range`() = trainerTest {
        val viewModel = viewModel()

        // A long time is still a time: only the far end of the slider means "throughout".
        viewModel.setShowSeconds(100.0)
        assertEquals(PhraseTrainerUiState.SHOW_SECONDS_RANGE.endInclusive, viewModel.uiState.value.showSeconds)

        viewModel.setShowSeconds(-1.0)
        assertEquals(PhraseTrainerUiState.SHOW_SECONDS_OPTIONS.first(), viewModel.uiState.value.showSeconds)
    }

    // --- ready-made runs --------------------------------------------------------------------

    @Test
    fun `a preset decides the whole run`() = trainerTest {
        val state = viewModel(
            preset = PhraseTrainerPreset(
                source = PhraseSource.Library(PhraseLibrary.all.first().id),
                checkDuration = true,
            ),
        ).uiState.value

        assertEquals(PhraseLibrary.all.first(), state.phrase)
        assertTrue(state.checkDuration)
        assertTrue(state.isPreconfigured)
    }

    @Test
    fun `a preset asks to start exactly once`() = trainerTest {
        val viewModel = viewModel(
            preset = PhraseTrainerPreset(source = PhraseSource.Library(PhraseLibrary.all.first().id)),
        )

        assertTrue(viewModel.consumeAutoStart())
        assertFalse(viewModel.consumeAutoStart(), "rotation must not start it again")
    }

    // --- the round trip ---------------------------------------------------------------------

    @Test
    fun `every choice survives being written down and started again`() = trainerTest {
        val configured = viewModel()
        configured.selectScaleChoice("d_major")
        configured.selectPattern(TestPatterns.catalog.all.last().id)
        configured.setCheckDuration(true)
        configured.selectRepeats(3)
        configured.setMixed(true)
        configured.setShowSeconds(4.0)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(phraseTrainerPresetOf(written, patterns = TestPatterns.catalog, editable = true))
        val started = viewModel(preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `an untouched setup is written down when it is asked for`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.PHRASE_TRAINER).isEmpty)
    }

    private companion object {
        const val FRAME_DURATION_MS = 40L
    }

    // --- written spelling ---------------------------------------------------------------------

    /** One bar written with flats in a phrase whose fallback style is sharps. */
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

    private fun written(token: String) = assertNotNull(SpelledNote.parse(token))

    @Test
    fun `a phrase's notes are asked for as written, and a heard note is written the phrase's way`() =
        trainerTest {
            val microphone = FakeMicrophone(FRAME_DURATION_MS)
            val viewModel = viewModel(microphone = microphone, phrases = flatPhrase)
            viewModel.start()
            advanceTimeBy(5_000)
            assertEquals(written("C4"), viewModel.uiState.value.progress?.currentNote)

            // The pitch of the phrase's E flat, played too early: named as the phrase writes it.
            microphone.frequencyHz = soundingFrequencyOf(Note(NoteName.D_SHARP, 4))
            advanceTimeBy(500)
            assertEquals(written("Eb4"), viewModel.uiState.value.progress?.detectedNote)

            // Nothing in the phrase sounds like this: the phrase's own style spells it.
            microphone.frequencyHz = soundingFrequencyOf(Note(NoteName.F_SHARP, 4))
            advanceTimeBy(500)
            assertEquals(written("F#4"), viewModel.uiState.value.progress?.detectedNote)

            microphone.frequencyHz = soundingFrequencyOf(Note(NoteName.C, 4))
            advanceTimeBy(2_000)
            assertEquals(written("Eb4"), viewModel.uiState.value.progress?.currentNote, "the next note, as written")

            viewModel.stop()
        }

    // --- what the counter counts, and a unit shown throughout ---------------------------------

    /** One bar with a rest in it: three notes to play, four steps. */
    private val withRest = InMemoryPhraseCatalog(
        parsePhrases(
            listOf(
                """
                {"id": "rest", "name": "Rest", "bars": [ { "steps": [
                  { "note": "C4", "duration": "quarter" }, { "duration": "quarter" },
                  { "note": "E4", "duration": "quarter" }, { "note": "G4", "duration": "quarter" }
                ] } ] }
                """.trimIndent(),
            ),
        ),
    )

    @Test
    fun `the counter counts the notes to play, not the rests`() = trainerTest {
        val microphone = FakeMicrophone(FRAME_DURATION_MS)
        val viewModel = viewModel(microphone = microphone, phrases = withRest)
        viewModel.start()
        advanceTimeBy(5_000)

        val first = viewModel.uiState.value.progress
        microphone.frequencyHz = soundingFrequencyOf(Note(NoteName.C, 4))
        advanceTimeBy(700)
        val second = viewModel.uiState.value.progress
        viewModel.stop()

        assertEquals(3, first?.stepCount)
        assertEquals(0, first?.stepIndex)
        // Past the C and over the rest: the E is the second note, not the third step.
        assertEquals(written("E4"), second?.currentNote)
        assertEquals(1, second?.stepIndex)
    }

    @Test
    fun `shown throughout, a unit is never taken away - it goes straight to being played`() = trainerTest {
        val viewModel = viewModel()
        viewModel.setShowSeconds(PhraseTrainerUiState.SHOW_THROUGHOUT)
        assertTrue(viewModel.uiState.value.showsThroughout)

        viewModel.start()
        val phases = mutableListOf<PhraseTrainerPhase>()
        repeat(40) {
            advanceTimeBy(50)
            phases += viewModel.uiState.value.phase
        }
        viewModel.stop()

        assertTrue(phases.none { it is PhraseTrainerPhase.Showing }, "phases were $phases")
        assertTrue(phases.any { it is PhraseTrainerPhase.Listening }, "phases were $phases")
    }

    @Test
    fun `the far end of the slider is written down and read back as throughout`() = trainerTest {
        val configured = viewModel()
        configured.setShowSeconds(PhraseTrainerUiState.SHOW_THROUGHOUT)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(phraseTrainerPresetOf(written, patterns = TestPatterns.catalog, editable = true))
        assertTrue(preset.showSeconds.isInfinite())
        assertTrue(viewModel(preset = preset).uiState.value.showsThroughout)
        assertTrue(viewModel().uiState.value.withSetup(written, PhraseLibrary, TestPatterns.catalog).showsThroughout)
    }

}

private val DEADLINE = 15.seconds

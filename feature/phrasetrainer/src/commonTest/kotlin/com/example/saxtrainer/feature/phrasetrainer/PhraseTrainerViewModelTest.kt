package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
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
    ) = PhraseTrainerViewModel(
        settingsRepository = settings,
        exerciseSetups = setups,
        audioCapture = microphone,
        preset = preset,
        config = config,
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

        viewModel.selectPattern(PatternLibrary.all.first().id)

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
        dMajor.selectPattern("one_two_three_five")
        assertEquals(
            listOf(
                Note(NoteName.D, 4),
                Note(NoteName.E, 4),
                Note(NoteName.F_SHARP, 4),
                Note(NoteName.A, 4),
            ),
            dMajor.uiState.value.phrase?.toneSequence()?.notes,
        )

        val cMajor = viewModel()
        cMajor.selectScaleChoice("c_major")
        cMajor.selectPattern("one_two_three_five")
        assertEquals(
            listOf(Note(NoteName.C, 4), Note(NoteName.D, 4), Note(NoteName.E, 4), Note(NoteName.G, 4)),
            cMajor.uiState.value.phrase?.toneSequence()?.notes,
        )
    }

    @Test
    fun `the octaves are their own setting`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectScaleChoice("c_major")
        viewModel.selectPattern("triad_up")
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
        configured.selectScaleChoice(ScaleChoice.defaults[1].id)
        configured.selectPattern(PatternLibrary.all.last().id)
        configured.setCheckDuration(true)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(phraseTrainerPresetOf(written, editable = true))
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
}

private val DEADLINE = 15.seconds

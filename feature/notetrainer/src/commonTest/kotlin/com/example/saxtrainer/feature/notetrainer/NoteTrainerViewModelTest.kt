package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Reading a note, playing it, and what the trainer makes of that. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteTrainerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * A test of this trainer, on virtual time and with a deadline.
     *
     * The fake microphone emits for ever, so an exercise that never ends leaves virtual time with
     * work to do and the test would hang rather than fail. The deadline turns that into a named
     * failure - what JUnit's Timeout rule did before this module was multiplatform.
     */
    private fun trainerTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher, timeout = DEADLINE, testBody = body)

    private val config = NoteTrainerConfig()
    private val frameDurationMs = 40L

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

        private fun TestScope.viewModel(
        microphone: AudioCapture? = FakeMicrophone(frameDurationMs),
        settings: SettingsRepository = FakeSettingsRepository(),
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
        preset: NoteTrainerPreset? = null,
        seed: Int = 1,
    ) = NoteTrainerViewModel(
        settingsRepository = settings,
        exerciseSetups = setups,
        audioCapture = microphone,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
        countInClock = MonotonicBeatClock(nanoTime = { testScheduler.currentTime * 1_000_000 }),
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    /** Puts exactly these notes into the custom set and switches to it, as the dialog would. */
    private fun NoteTrainerViewModel.selectOnly(vararg notes: Note) {
        editCustomNotes()
        clearCustomNotes()
        notes.forEach(::toggleCustomNote)
        applyCustomNotes()
        setRandomOrder(false)
    }

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    /**
     * Runs virtual time on for long enough to settle, and no longer.
     *
     * Deliberately not [advanceUntilIdle]: the fake microphone emits for ever, so "idle" never
     * arrives and a test that expected an exercise to end would hang instead of failing.
     */
    private fun TestScope.settle(millis: Long = 3_000) = advanceTimeBy(millis)

    private fun result(viewModel: NoteTrainerViewModel): NoteTrainerResult {
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is NoteTrainerPhase.Finished, "phase was $phase")
        return phase.result
    }

    // --- setup ------------------------------------------------------------------------------

    @Test
    fun `the beginner set is ready to go on the first run`() = trainerTest {
        val state = viewModel().uiState.value

        assertEquals(8, state.activeNotes.size)
        assertTrue(state.isRandom, "mixed order is the default")
        assertFalse(state.showFingeringHint, "the fingering is the answer, not the question")
        assertFalse(state.showNoteName, "the notation is the point")
        assertEquals(config.defaultTargetSeconds, state.targetSeconds)
        assertTrue(state.canStart)
    }

    @Test
    fun `without a microphone there is no exercise to run`() = trainerTest {
        val viewModel = viewModel()
        viewModel.onMicrophoneUnavailable()

        assertFalse(viewModel.uiState.value.canStart)

        viewModel.start()

        assertEquals(NoteTrainerPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `the setup is not the player's to change once it is running`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.start()
        advanceTimeBy(200)

        viewModel.setShowFingeringHint(true)
        viewModel.setRounds(3)

        assertFalse(viewModel.uiState.value.showFingeringHint)
        assertEquals(1, viewModel.uiState.value.rounds)

        viewModel.stop()
        settle()
    }

    // --- playing ------------------------------------------------------------------------------

    @Test
    fun `the note is written and the microphone is compared in concert pitch`() =
        trainerTest {
            // The tenor sounds a major ninth below what it reads, and the trainer has to know it.
            val microphone = FakeMicrophone(frameDurationMs)
            val viewModel = viewModel(microphone = microphone)
            viewModel.selectOnly(c4)

            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(400)

            assertEquals(Listening.CORRECT, viewModel.uiState.value.progress!!.listening)

            viewModel.stop()
            settle()
        }

    @Test
    fun `playing the note moves on to the next one`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        // Held long enough to count, plus the moment the note stays green.
        advanceTimeBy(1_200)

        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)
        assertEquals(d4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        settle()
    }

    @Test
    fun `the reaction is measured to the first moment the note was heard`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(1_000)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        // A second of looking for it, and the wait that confirms the note is not added on top.
        assertNotNull(attempt.reactionSeconds)
        assertTrue(
            attempt.reactionSeconds in 0.9..1.3,
            "reaction was ${attempt.reactionSeconds}",
        )
        assertFalse(attempt.skipped)
    }

    @Test
    fun `a wrong note is counted and named, and the note stays put`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(e4)
        advanceTimeBy(1_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.WRONG_PITCH, progress.listening)
        // Named as the player would read it, not as it sounds in the room.
        assertEquals(e4, progress.detectedNote)
        assertEquals(1, progress.wrongTries)
        assertEquals(0, progress.noteIndex, "a wrong note does not move the exercise on")

        viewModel.stop()
        settle()

        // Stopped in the middle of a note nobody finished: it was neither played nor skipped, so
        // there is nothing honest to record about it.
        assertTrue(result(viewModel).attempts.isEmpty())
    }

    @Test
    fun `the right note after a wrong one still counts`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(e4)
        advanceTimeBy(500)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        assertFalse(attempt.skipped)
        assertEquals(1, attempt.wrongTries)
        assertNotNull(attempt.reactionSeconds)
    }

    @Test
    fun `a sound with no pitch in it is not a wrong try`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs, noisy = true)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(1_500)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.UNCLEAR, progress.listening)
        assertEquals(0, progress.wrongTries, "an unclear sound accuses nobody of anything")
        assertNull(progress.detectedNote)

        viewModel.stop()
        settle()
    }

    @Test
    fun `silence is not a wrong try either`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(2_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.WAITING, progress.listening)
        assertEquals(0, progress.wrongTries)

        viewModel.stop()
        settle()
    }

    @Test
    fun `a note nobody plays is waited for, however long that takes`() = trainerTest {
        // No timeout anywhere: needing longer to work out a note is not a failure.
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(5_000)

        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)
        assertTrue(viewModel.uiState.value.phase is NoteTrainerPhase.Running)

        viewModel.stop()
        settle()
    }

    @Test
    fun `skipping gives up on a note without pretending it was played`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(1_000)
        viewModel.skip()
        advanceTimeBy(200)

        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        settle()

        val skipped = result(viewModel).attempts.first()
        assertTrue(skipped.skipped)
        assertNull(skipped.reactionSeconds, "a note that was never played has no reaction time")
    }

    // --- pause ---------------------------------------------------------------------------------

    @Test
    fun `a held exercise analyses nothing`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        viewModel.togglePause()
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(5_000)

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(0, viewModel.uiState.value.progress!!.noteIndex, "nothing moved while it was held")

        viewModel.stop()
        settle()
    }

    @Test
    fun `continuing puts the note up again rather than counting the pause`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(200)
        viewModel.togglePause()
        advanceTimeBy(5_000)
        viewModel.togglePause()

        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        settle()

        val attempt = result(viewModel).attempts.single()
        assertTrue(
            attempt.reactionSeconds!! < 2.0,
            "the pause was counted as thinking time: ${attempt.reactionSeconds}",
        )
    }

    // --- a whole run ---------------------------------------------------------------------------

    @Test
    fun `the exercise ends after the last note`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(1_200)
        microphone.frequencyHz = soundingFrequencyOf(d4)
        settle()

        val result = result(viewModel)
        assertTrue(result.completed)
        assertEquals(listOf(c4, d4), result.attempts.map { it.note })
        assertEquals(2, result.correctCount)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `every round plays every note`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRounds(3)

        viewModel.start()
        advanceTimeBy(200)
        repeat(6) {
            viewModel.skip()
            advanceTimeBy(200)
        }
        settle()

        assertEquals(6, result(viewModel).attempts.size)
    }

    @Test
    fun `stopping early keeps the notes that were played`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4, e4)

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(1_200)

        viewModel.stop()
        settle()

        val result = result(viewModel)
        assertFalse(result.completed)
        assertEquals(1, result.attempts.size)
        assertEquals(3, result.plannedNoteCount)
    }

    // --- the count-in ---------------------------------------------------------------------------

    @Test
    fun `the exercise counts the player in before its first note`() = trainerTest {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(NoteTrainerPhase.CountIn(4), viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.progress, "nothing is asked for during the count")

        // Two quiet lead-in beats and one counted one, at the reference sixty.
        advanceTimeBy(3_100)
        assertEquals(NoteTrainerPhase.CountIn(3), viewModel.uiState.value.phase)

        viewModel.stop()
        settle()
    }

    @Test
    fun `stopping during the count-in reports nothing`() = trainerTest {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(500)
        viewModel.stop()
        settle()

        assertEquals(NoteTrainerPhase.Idle, viewModel.uiState.value.phase)
    }

    // --- ready-made exercises --------------------------------------------------------------------

    @Test
    fun `a preset decides the whole exercise`() = trainerTest {
        val state = viewModel(
            preset = NoteTrainerPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                rounds = 2,
                showFingeringHint = true,
                targetSeconds = 8,
            ),
        ).uiState.value

        assertEquals(listOf(c4, d4, e4), state.activeNotes)
        assertEquals(NoteOrder.ORDERED, state.order)
        assertEquals(2, state.rounds)
        assertTrue(state.showFingeringHint)
        assertEquals(8, state.targetSeconds)
        assertTrue(state.isPreconfigured)
    }

    @Test
    fun `the hint shows the fingering while the note is being asked for`() = trainerTest {
        val viewModel = viewModel(
            preset = NoteTrainerPreset(notes = listOf(c4), showFingeringHint = true, autoStart = false),
        )

        viewModel.start()
        advanceTimeBy(200)

        assertNotNull(viewModel.uiState.value.progress!!.fingering)

        viewModel.stop()
        settle()
    }

    @Test
    fun `without the hint the fingering is not offered`() = trainerTest {
        val viewModel = viewModel(preset = NoteTrainerPreset(notes = listOf(c4), autoStart = false))

        viewModel.start()
        advanceTimeBy(200)

        assertNull(viewModel.uiState.value.progress!!.fingering)

        viewModel.stop()
        settle()
    }

    @Test
    fun `a preset asks to start exactly once`() = trainerTest {
        val viewModel = viewModel(preset = NoteTrainerPreset(notes = listOf(c4)))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse(viewModel.consumeAutoStart(), "rotation must not start it again")
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = trainerTest {
        val state = viewModel(
            preset = NoteTrainerPreset(notes = listOf(c4, Note(NoteName.C, 2))),
        ).uiState.value

        assertEquals(listOf(c4), state.activeNotes)
    }

    // --- the result -------------------------------------------------------------------------------

    @Test
    fun `the middle reaction is reported, not the average`() {
        // One long think should not hide a run that was otherwise quick.
        val result = NoteTrainerResult(
            attempts = listOf(
                NoteAttempt(c4, reactionSeconds = 1.0, wrongTries = 0, skipped = false),
                NoteAttempt(d4, reactionSeconds = 2.0, wrongTries = 0, skipped = false),
                NoteAttempt(e4, reactionSeconds = 30.0, wrongTries = 0, skipped = false),
            ),
            plannedNoteCount = 3,
        )

        assertEquals(2.0, result.medianReactionSeconds!!, 1e-9)
        assertEquals(2, result.withinTarget(4))
    }

    @Test
    fun `a skipped note has no reaction to report`() {
        val result = NoteTrainerResult(
            attempts = listOf(
                NoteAttempt(c4, reactionSeconds = null, wrongTries = 0, skipped = true),
            ),
            plannedNoteCount = 1,
        )

        assertNull(result.medianReactionSeconds)
        assertEquals(0, result.correctCount)
        assertEquals(0, result.withinTarget(4))
    }

    // --- holding ------------------------------------------------------------------------------

    @Test
    fun `with no hold asked for the note counts as soon as it is right`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(500)

            val progress = viewModel.uiState.value.progress!!
            assertTrue(progress.landed, "the note was confirmed and is on its way out")
            assertNull(progress.holdFraction, "there is no hold to be part way through")
        } finally {
            viewModel.stop()
            settle()
        }
    }

    @Test
    fun `a hold has to be kept for as long as it asks`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setHoldSeconds(2.0)

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_000)

            val holding = viewModel.uiState.value.progress!!
            assertEquals(0, holding.noteIndex, "a second of a two second hold is not a played note")
            assertEquals(Listening.CORRECT, holding.listening)
            assertFalse(holding.landed)
            assertTrue(holding.isHolding)
            assertNotNull(holding.holdFraction)
            assertTrue(holding.holdFraction < 1f, "the hold is under way, not finished")

            advanceTimeBy(2_000)

            assertEquals(1, viewModel.uiState.value.progress!!.noteIndex, "the hold was kept")
        } finally {
            viewModel.stop()
            settle()
        }
    }

    @Test
    fun `losing the note starts its hold again from nothing`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setHoldSeconds(2.0)

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_500)
            // Dropped and picked up again: two stretches of a second and a half are not a hold of
            // three seconds.
            microphone.frequencyHz = null
            advanceTimeBy(400)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_500)

            assertEquals(0, viewModel.uiState.value.progress!!.noteIndex, "the hold began again")

            // The rest of the two second hold, and the moment the note stays green after it.
            advanceTimeBy(2_000)

            assertEquals(1, viewModel.uiState.value.progress!!.noteIndex, "and was then kept")
        } finally {
            viewModel.stop()
            settle()
        }
    }

    @Test
    fun `a wrong note in the middle of a hold starts it again`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setHoldSeconds(2.0)

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_500)
            microphone.frequencyHz = soundingFrequencyOf(e4)
            advanceTimeBy(400)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_500)

            assertEquals(0, viewModel.uiState.value.progress!!.noteIndex)
        } finally {
            viewModel.stop()
            settle()
        }
    }

    @Test
    fun `a hold never makes the player look slower than they were`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4)
        viewModel.setHoldSeconds(2.0)

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            settle()

            // Found in well under a second; the two seconds spent proving it are not the reaction.
            val reaction = result(viewModel).attempts.single().reactionSeconds!!
            assertTrue(reaction < 1.0, "reaction was $reaction s")
        } finally {
            viewModel.stop()
            settle()
        }
    }

    // --- remembering the setup ------------------------------------------------------------------

    @Test
    fun `the setup a run was started with is what the next one opens on`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        val first = viewModel(setups = setups)
        first.selectOnly(c4, e4)
        first.setRounds(3)
        first.setShowFingeringHint(true)
        first.setShowNoteName(true)
        first.setTargetSeconds(8)
        first.setHoldSeconds(2.0)

        first.start()
        advanceTimeBy(200)
        first.stop()
        settle()

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(listOf(c4, e4), opened.activeNotes)
        assertEquals(3, opened.rounds)
        assertTrue(opened.showFingeringHint)
        assertTrue(opened.showNoteName)
        assertEquals(8, opened.targetSeconds)
        assertEquals(2.0, opened.holdSeconds)
        assertFalse(opened.isRandom)
    }

    @Test
    fun `a setup the player never started is not remembered`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        val fiddled = viewModel(setups = setups)
        fiddled.setRounds(3)
        fiddled.setHoldSeconds(3.0)

        assertTrue(setups.setupOf(Exercise.NOTE_TRAINER).isEmpty)
        assertEquals(config.defaultRounds, viewModel(setups = setups).uiState.value.rounds)
    }

    @Test
    fun `a ready-made exercise is not remembered as the player's own`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        val preset = NoteTrainerPreset(notes = listOf(c4), rounds = 2, autoStart = false)

        val viewModel = viewModel(setups = setups, preset = preset)
        viewModel.start()
        advanceTimeBy(200)
        viewModel.stop()
        settle()

        assertTrue(setups.setupOf(Exercise.NOTE_TRAINER).isEmpty)
    }
}

/** Long enough for any exercise here, short enough that a hang is reported rather than waited on. */
private val DEADLINE = 15.seconds

package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.SpelledNote
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
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

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

    /** Chooses exactly these notes in the note window and applies them, as the player would. */
    private fun NoteTrainerViewModel.selectOnly(vararg notes: SpelledNote) {
        openNotePicker()
        updateNotePicker { picker -> notes.fold(picker.copy(chosen = emptySet())) { acc, note -> acc.toggle(note) } }
        applyNotePicker()
        setRandomOrder(false)
    }

    private val c4 = SpelledNote.of(Note(NoteName.C, 4))
    private val d4 = SpelledNote.of(Note(NoteName.D, 4))
    private val e4 = SpelledNote.of(Note(NoteName.E, 4))

    private fun written(token: String) = assertNotNull(SpelledNote.parse(token))

    /** What a written note sounds like on the default tenor saxophone - its pitch, whatever its spelling. */
    private fun soundingFrequencyOf(written: SpelledNote) =
        Instrument.TENOR_SAX.transposition.concertFor(written.note).frequencyHz()

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
        assertEquals(config.quickSeconds, state.targetSeconds)
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
            ),
        ).uiState.value

        assertEquals(listOf(c4, d4, e4), state.activeNotes)
        assertEquals(NoteOrder.ORDERED, state.order)
        assertEquals(2, state.rounds)
        assertTrue(state.showFingeringHint)
        assertTrue(state.isPreconfigured)
    }

    // --- written spelling ------------------------------------------------------------------

    @Test
    fun `a preset keeps every note as it is written, one pitch in two spellings included`() = trainerTest {
        val dSharp = written("D#4")
        val eFlat = written("Eb4")
        val aFlat = written("Ab4")
        val state = viewModel(preset = NoteTrainerPreset(notes = listOf(c4, dSharp, eFlat, aFlat))).uiState.value

        assertEquals(listOf(c4, dSharp, eFlat, aFlat), state.activeNotes)
        assertEquals(listOf("C4", "D♯4", "E♭4", "A♭4"), state.activeNotes.map(state::nameOf))
        assertEquals(dSharp.note, eFlat.note, "two spellings, one pitch")
    }

    @Test
    fun `a flat chosen by hand is still a flat when the trainer is opened again`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        val chosen = listOf(c4, d4, written("Eb4"), written("F4"), written("G4"), written("Ab4"), written("Bb4"))
        viewModel(setups = setups).selectOnly(*chosen.toTypedArray())

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(chosen, opened.activeNotes)
        assertEquals(
            listOf("C4", "D4", "E♭4", "F4", "G4", "A♭4", "B♭4"),
            opened.activeNotes.map(opened::nameOf),
        )
    }

    @Test
    fun `a saved practice keeps its spelling when started and when reopened to be edited`() = trainerTest {
        val configured = viewModel()
        configured.selectOnly(c4, written("D#4"), written("Eb4"), written("Cb5"))
        val saved = configured.uiState.value.toSetup()

        val started = assertNotNull(noteTrainerPresetOf(saved))
        assertEquals(listOf(c4, written("D#4"), written("Eb4"), written("Cb5")), started.notes)

        val edited = viewModel(preset = assertNotNull(noteTrainerPresetOf(saved, editable = true))).uiState.value
        assertEquals(setOf(c4, written("D#4"), written("Eb4"), written("Cb5")), edited.customNotes)
        assertEquals(saved, edited.toSetup(), "editing it writes down the same spellings again")
    }

    @Test
    fun `a saved ready-made set reopened to be edited is still that set`() = trainerTest {
        val configured = viewModel()
        configured.selectNoteSet(NoteSet.BEGINNER_PLUS)
        val saved = configured.uiState.value.toSetup()

        val edited = viewModel(preset = assertNotNull(noteTrainerPresetOf(saved, editable = true))).uiState.value

        assertEquals(NoteSet.BEGINNER_PLUS, edited.noteSet, "not turned into the player's own selection")
        assertEquals(configured.uiState.value.activeNotes, edited.activeNotes)
    }

    @Test
    fun `a setup saved before spellings were kept opens with the usual spellings`() = trainerTest {
        val old = exerciseSetup {
            put("note_set", "CUSTOM")
            putInts("custom_notes", listOf(60, 63, 66, 70))
        }

        val preset = assertNotNull(noteTrainerPresetOf(old))

        assertEquals(listOf("C4", "D♯4", "F♯4", "B♭4"), preset.notes.map { NoteNaming.INTERNATIONAL.labelOf(it) })
    }

    @Test
    fun `a heard note is written the way the exercise writes it, or as it usually is`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, written("Eb4"))

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(written("D#4"))
        advanceTimeBy(1_000)
        assertEquals(written("Eb4"), viewModel.uiState.value.progress!!.detectedNote, "the exercise writes this pitch as E flat")

        microphone.frequencyHz = soundingFrequencyOf(written("F#4"))
        advanceTimeBy(1_000)
        assertEquals(written("F#4"), viewModel.uiState.value.progress!!.detectedNote, "nothing in the exercise sounds like it")

        viewModel.stop()
        settle()
    }

    @Test
    fun `a pitch the player plays is checked as a pitch, however the note is written`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(written("Cb5"))

        viewModel.start()
        advanceTimeBy(200)
        microphone.frequencyHz = soundingFrequencyOf(written("B4"))
        advanceTimeBy(400)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Listening.CORRECT, progress.listening, "C flat 5 is fingered and heard as B4")
        assertEquals(written("Cb5"), progress.detectedNote)

        viewModel.stop()
        settle()
    }

    @Test
    fun `the note window opens on the notes in use, and offers every spelling`() = trainerTest {
        val viewModel = viewModel()
        viewModel.selectNoteSet(NoteSet.BEGINNER_PLUS)
        viewModel.openNotePicker()

        val state = viewModel.uiState.value
        val picker = state.notePicker!!
        assertEquals(state.activeNotes.toSet(), picker.chosen)
        assertTrue(state.availableNotes.size > picker.chosen.size)
        assertTrue(written("Eb4") in picker.choices && written("D#4") in picker.choices)
    }

    @Test
    fun `starting from chromatic is stored as the chromatic set, anything else as custom`() = trainerTest {
        val viewModel = viewModel()
        viewModel.openNotePicker()
        viewModel.startNotePickerFrom(NoteSet.CHROMATIC)
        viewModel.applyNotePicker()

        assertEquals(NoteSet.CHROMATIC, viewModel.uiState.value.noteSet)
        assertNull(viewModel.uiState.value.notePicker, "the window closes")

        viewModel.openNotePicker()
        viewModel.updateNotePicker { it.toggle(written("D#4")) }
        viewModel.applyNotePicker()

        val state = viewModel.uiState.value
        assertEquals(NoteSet.CUSTOM, state.noteSet)
        assertTrue(written("D#4") !in state.activeNotes, "the sharp was taken out")
        assertEquals(state.activeNotes.toSet(), state.customNotes)
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
            preset = NoteTrainerPreset(notes = listOf(c4, SpelledNote.of(Note(NoteName.C, 2)))),
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
        viewModel.setHoldBeats(Beats.of(2))

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
        viewModel.setHoldBeats(Beats.of(2))

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
        viewModel.setHoldBeats(Beats.of(2))

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
    fun `one stray frame during a hold does not start it again`() = trainerTest {
        val microphone = FakeMicrophone(frameDurationMs)
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(c4, d4)
        viewModel.setHoldBeats(Beats.of(2))

        try {
            viewModel.start()
            advanceTimeBy(200)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(1_500)
            // One glitch frame - a reed squeak, a harmonic the detector snapped onto for an
            // instant - and straight back, unlike the sustained change in the test above.
            microphone.frequencyHz = soundingFrequencyOf(e4)
            advanceTimeBy(frameDurationMs)
            microphone.frequencyHz = soundingFrequencyOf(c4)
            advanceTimeBy(2_000)

            assertEquals(1, viewModel.uiState.value.progress!!.noteIndex, "the hold survived one stray frame")
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
        viewModel.setHoldBeats(Beats.of(2))

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
        first.setHoldBeats(Beats.of(2))

        first.start()
        advanceTimeBy(200)
        first.stop()
        settle()

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(listOf(c4, e4), opened.activeNotes)
        assertEquals(3, opened.rounds)
        assertTrue(opened.showFingeringHint)
        assertTrue(opened.showNoteName)
        assertEquals(Beats.of(2), opened.holdBeats)
        assertFalse(opened.isRandom)
    }

    @Test
    fun `a setup is kept without waiting for a run to be started`() = trainerTest {
        // Going back throws the ViewModel away, so a choice not written down by then is lost. This
        // is the setup screen being left rather than started, which is how most of them are left.
        val setups = InMemoryExerciseSetupRepository()
        val chosen = viewModel(setups = setups)
        chosen.setRounds(3)
        chosen.setHoldBeats(Beats.of(4))

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(3, opened.rounds)
        assertEquals(Beats.of(4), opened.holdBeats)
    }

    @Test
    fun `the notes chosen by hand are kept, chosen and unchosen alike`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        viewModel(setups = setups).selectOnly(c4, e4)

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(setOf(c4, e4), opened.customNotes)
        assertEquals(listOf(c4, e4), opened.activeNotes, "in pitch order, whatever order they were ticked")
        assertFalse(d4 in opened.customNotes, "a note left unticked stays unticked")
        assertTrue(setups.setupOf(Exercise.LONG_TONES).isEmpty, "one exercise, one setup")
    }

    @Test
    fun `a new selection replaces the one before it`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        viewModel(setups = setups).selectOnly(c4, d4, e4)
        viewModel(setups = setups).selectOnly(d4)

        val opened = viewModel(setups = setups).uiState.value

        assertEquals(setOf(d4), opened.customNotes)
        assertEquals(listOf(d4), opened.activeNotes)
    }

    @Test
    fun `an empty draft cannot be applied`() = trainerTest {
        val viewModel = viewModel()
        val before = viewModel.uiState.value.activeNotes
        viewModel.openNotePicker()
        viewModel.updateNotePicker { it.copy(chosen = emptySet()) }
        viewModel.applyNotePicker()

        val state = viewModel.uiState.value
        assertNotNull(state.notePicker, "the window stays open")
        assertEquals(before, state.activeNotes)
    }

    @Test
    fun `notes ticked in the window count for nothing until they are applied`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()
        val fiddled = viewModel(setups = setups)
        fiddled.selectOnly(c4, e4)
        fiddled.openNotePicker()
        fiddled.updateNotePicker { it.toggle(d4) }
        fiddled.closeNotePicker()

        assertEquals(setOf(c4, e4), viewModel(setups = setups).uiState.value.customNotes)
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

    // --- the round trip ---------------------------------------------------------------------

    /**
     * Every choice the setup screen offers survives being written down, turned back into a
     * ready-made exercise, and written down again.
     *
     * A stored setup has two ways out - one opens the setup screen on it, the other starts it - and
     * they have to agree. Where they do not, the difference shows only in an exercise somebody
     * saved, and only once it is started, which is the worst place to find it.
     */
    @Test
    fun `every choice survives being written down and started again`() = trainerTest {
        val configured = viewModel()
        configured.selectOnly(c4, d4, e4)
        configured.setRandomOrder(false)
        configured.setRounds(3)
        configured.setShowFingeringHint(true)
        configured.setShowNoteName(true)
        configured.setHoldBeats(Beats.of(2))
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(noteTrainerPresetOf(written, editable = true))
        val started = viewModel(preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `the name under the staff is part of the exercise`() = trainerTest {
        val configured = viewModel()
        configured.selectOnly(c4, d4)
        configured.setShowNoteName(true)

        val preset = assertNotNull(noteTrainerPresetOf(configured.uiState.value.toSetup()))

        assertTrue(preset.showNoteName, "the note name was chosen")
    }

    @Test
    fun `a named set of notes comes back as the notes themselves`() = trainerTest {
        val configured = viewModel()
        configured.selectNoteSet(NoteSet.BEGINNER)
        val expected = configured.uiState.value.activeNotes

        val preset = assertNotNull(noteTrainerPresetOf(configured.uiState.value.toSetup()))

        // The label collapses - a preset names notes, not a set - but not one note changes.
        assertEquals(expected, preset.notes)
    }


    /**
     * A practice nobody has touched is still a practice.
     *
     * The setup is written down as it is changed, so an exercise opened and left on its defaults
     * has nothing on record - and "add to your lessons" would have found nothing to save. Asking
     * for it writes it.
     */
    @Test
    fun `an untouched setup is written down when it is asked for`() = trainerTest {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.NOTE_TRAINER).isEmpty)
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
        val preset = assertNotNull(noteTrainerPresetOf(ExerciseSetup()))

        assertTrue(preset.notes.isNotEmpty())
    }

}

/** Long enough for any exercise here, short enough that a hang is reported rather than waited on. */
private val DEADLINE = 15.seconds

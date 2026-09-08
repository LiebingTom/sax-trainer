package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.exercise.MonotonicBeatClock
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.NoteSets
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.TrainingTool
import com.example.saxtrainer.core.testing.FakeMicrophone
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
import com.example.saxtrainer.core.music.Beats
import kotlin.test.assertNotNull
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.Exercise
import com.example.saxtrainer.core.settings.ExerciseSetup

@OptIn(ExperimentalCoroutinesApi::class)
class FingeringViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val config = FingeringConfig()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.virtualClock() = FingeringSessionClock(
        tickIntervalMs = config.tickIntervalMs,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    private fun TestScope.virtualBeatClock() = MonotonicBeatClock(
        tickIntervalMs = config.tickIntervalMs,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
    )

    private fun TestScope.viewModel(
        seed: Int = 1,
        microphone: AudioCapture? = null,
        settings: SettingsRepository = FakeSettingsRepository(),
        preset: FingeringPreset? = null,
        setups: ExerciseSetupRepository = InMemoryExerciseSetupRepository(),
    ) = FingeringViewModel(
        settingsRepository = settings,
        exerciseSetups = setups,
        audioCapture = microphone,
        preset = preset,
        config = config,
        random = Random(seed),
        processingDispatcher = dispatcher,
        clock = virtualClock(),
        countInClock = virtualBeatClock(),
    )

    /** Puts exactly these notes into the custom set and switches to it, as the dialog would. */
    private fun FingeringViewModel.selectOnly(vararg notes: Note) {
        editCustomNotes()
        clearCustomNotes()
        notes.forEach(::toggleCustomNote)
        applyCustomNotes()
    }

    /** What a written note sounds like on the default tenor saxophone. */
    private fun soundingFrequencyOf(written: Note) =
        Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

    private val c4 = Note(NoteName.C, 4)
    private val d4 = Note(NoteName.D, 4)
    private val e4 = Note(NoteName.E, 4)

    @Test
    fun `the beginner set is ready to go on the first run`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(SaxophoneFingerings.supportedNotes, state.availableNotes)
        assertEquals(NoteSet.BEGINNER, state.noteSet)
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            state.activeNotes.map { it.label },
            "the beginner set leaves out the sharps and flats",
        )
        assertTrue(state.isRandom, "mixed order is the default")
        assertFalse(state.checkPlaying, "listening is off until asked for")
        assertEquals(Beats.of(config.defaultBeats), state.beatsPerNote)
        assertTrue(state.canStart)
    }

    @Test
    fun `choosing a ready made set uses it straight away`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.selectNoteSet(NoteSet.BEGINNER_PLUS)

        val state = viewModel.uiState.value
        assertEquals(NoteSet.BEGINNER_PLUS, state.noteSet)
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "F#4", "G4", "A4", "A#4", "B4", "C5"),
            state.activeNotes.map { it.label },
            "the beginner sets stay small even though the chart covers the whole instrument",
        )
        assertTrue(state.canStart)
    }

    @Test
    fun `the custom set holds its own notes`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.selectOnly(c4, e4)
        assertEquals(NoteSet.CUSTOM, viewModel.uiState.value.noteSet)
        assertEquals(listOf(c4, e4), viewModel.uiState.value.activeNotes)

        // Switching away and back keeps what was chosen.
        viewModel.selectNoteSet(NoteSet.BEGINNER)
        assertEquals(setOf(c4, e4), viewModel.uiState.value.customNotes)

        viewModel.selectNoteSet(NoteSet.CUSTOM)
        assertEquals(listOf(c4, e4), viewModel.uiState.value.activeNotes)
    }

    @Test
    fun `the dialog only changes anything when it is applied`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.editCustomNotes()
        assertTrue(viewModel.uiState.value.isEditingCustomNotes)
        viewModel.toggleCustomNote(e4)
        viewModel.toggleCustomNote(c4)

        assertEquals(
            setOf(c4, d4),
            viewModel.uiState.value.customNotes,
            "the stored selection is untouched while editing",
        )

        viewModel.applyCustomNotes()
        assertEquals(setOf(d4, e4), viewModel.uiState.value.customNotes)
        assertEquals(listOf(d4, e4), viewModel.uiState.value.activeNotes)
        assertFalse(viewModel.uiState.value.isEditingCustomNotes)
    }

    @Test
    fun `cancelling the dialog restores the previous selection`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)

        viewModel.editCustomNotes()
        viewModel.clearCustomNotes()
        viewModel.toggleCustomNote(e4)
        viewModel.cancelCustomNotes()

        assertFalse(viewModel.uiState.value.isEditingCustomNotes)
        assertEquals(setOf(c4, d4), viewModel.uiState.value.customNotes)
        assertEquals(listOf(c4, d4), viewModel.uiState.value.activeNotes)
    }

    @Test
    fun `an empty custom set cannot be started`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.editCustomNotes()
        viewModel.clearCustomNotes()
        viewModel.applyCustomNotes()

        assertTrue(viewModel.uiState.value.activeNotes.isEmpty())
        assertFalse(viewModel.uiState.value.canStart)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a ready made set gets the player going again after an empty custom set`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.editCustomNotes()
            viewModel.clearCustomNotes()
            viewModel.applyCustomNotes()

            viewModel.selectNoteSet(NoteSet.BEGINNER)

            assertTrue(viewModel.uiState.value.canStart)
        }

    @Test
    fun `the first note and its fingering are shown at once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)

        viewModel.start()
        advanceTimeBy(10)

        val progress = viewModel.uiState.value.progress!!
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(c4, progress.currentNote)
        assertEquals(SaxophoneFingerings.fingeringFor(c4), progress.currentFingering)
        assertEquals(d4, progress.nextNote)
        assertEquals(1, progress.displayPosition)
        assertEquals(3, progress.noteCount)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `notes advance on the exercise clock`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(3))

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(3_000)
        assertEquals(d4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(3_000)
        assertEquals(e4, viewModel.uiState.value.progress!!.currentNote)
        assertNull(viewModel.uiState.value.progress!!.nextNote, "nothing follows the last note")

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `remaining time counts down within a note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setBeatsPerNote(Beats.of(5))

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(5f, viewModel.uiState.value.progress!!.remainingSeconds, 0.1f)

        advanceTimeBy(2_000)
        viewModel.uiState.value.progress!!.let {
            assertEquals(3f, it.remainingSeconds, 0.1f)
            assertEquals(0.4f, it.noteFraction, 0.05f)
        }

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the exercise ends after the last note`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(3))

        viewModel.start()
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is FingeringPhase.Finished, "phase was $phase")
        val result = phase.result
        assertTrue(result.completed)
        assertEquals(listOf(c4, d4), result.notes.map { it.note })
        assertFalse(result.checked, "nothing was listened to")
        assertEquals(0, result.correctCount)
        assertNull(viewModel.uiState.value.progress)
    }

    // --- count-in and pause ---------------------------------------------------------------------

    @Test
    fun `the exercise counts the player in before its first note`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(10)
        assertEquals(FingeringPhase.CountIn(4), viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.progress, "no note is shown during the count")

        // Two quiet lead-in beats come first, so the count still stands at four well into them.
        advanceTimeBy(1_500)
        assertEquals(FingeringPhase.CountIn(4), viewModel.uiState.value.phase)

        // The trainer has no tempo of its own, so it counts at its reference sixty: a beat a second.
        advanceTimeBy(1_500)
        assertEquals(FingeringPhase.CountIn(3), viewModel.uiState.value.phase)

        advanceTimeBy(3_000)
        assertEquals(FingeringPhase.Running, viewModel.uiState.value.phase)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `no count-in means the first note is there straight away`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 0)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(10)

        assertEquals(FingeringPhase.Running, viewModel.uiState.value.phase)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `stopping during the count-in reports nothing`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        advanceTimeBy(1_000)
        viewModel.stop()
        advanceUntilIdle()

        // Nothing was practised, so there is nothing to say about it.
        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `a paused exercise does not move on`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(5))

        viewModel.start()
        advanceTimeBy(2_000)
        viewModel.togglePause()
        val heldAt = viewModel.uiState.value.progress!!.remainingSeconds

        advanceTimeBy(60_000)

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(FingeringPhase.Running, viewModel.uiState.value.phase)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)
        assertEquals(heldAt, viewModel.uiState.value.progress!!.remainingSeconds, 0.1f)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `continuing picks the note up where it was left`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(5))

        viewModel.start()
        advanceTimeBy(2_000)
        viewModel.togglePause()
        advanceTimeBy(60_000)
        viewModel.togglePause()

        assertFalse(viewModel.uiState.value.isPaused)

        // Three seconds of the first note were still to come, and they are what comes next.
        advanceTimeBy(2_000)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        advanceTimeBy(1_100)
        assertEquals(d4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `pausing is not stopping`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setBeatsPerNote(Beats.of(5))

        viewModel.start()
        advanceTimeBy(1_000)
        viewModel.togglePause()
        advanceTimeBy(60_000)

        // A held exercise publishes no result: it is still there to come back to.
        assertFalse(viewModel.uiState.value.phase is FingeringPhase.Finished)

        viewModel.stop()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is FingeringPhase.Finished)
        assertFalse(viewModel.uiState.value.isPaused, "the pause was released by stopping")
    }

    @Test
    fun `the count-in is held too`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(countInBeats = 4)))
        viewModel.selectOnly(c4)

        viewModel.start()
        // Two lead-in beats and one counted one, all at the reference sixty.
        advanceTimeBy(3_100)
        assertEquals(FingeringPhase.CountIn(3), viewModel.uiState.value.phase)

        viewModel.togglePause()
        advanceTimeBy(60_000)
        assertEquals(FingeringPhase.CountIn(3), viewModel.uiState.value.phase)

        viewModel.togglePause()
        advanceTimeBy(3_000)
        assertEquals(FingeringPhase.Running, viewModel.uiState.value.phase)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `stopping early keeps the notes already practised`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4, e4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(3))

        viewModel.start()
        advanceTimeBy(4_000)
        viewModel.stop()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertFalse(result.completed)
        assertEquals(listOf(c4), result.notes.map { it.note })
        assertEquals(3, result.plannedNoteCount)
    }

    @Test
    fun `nothing advances while the clock stands still`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)

        viewModel.start()
        advanceTimeBy(10)
        val first = viewModel.uiState.value.progress!!

        assertEquals(first, viewModel.uiState.value.progress)
        assertEquals(c4, viewModel.uiState.value.progress!!.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the setup is locked while an exercise runs`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4, d4)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(5))

        viewModel.start()
        advanceTimeBy(100)

        viewModel.selectNoteSet(NoteSet.BEGINNER_PLUS)
        viewModel.editCustomNotes()
        viewModel.setBeatsPerNote(Beats.of(8))
        viewModel.setRandomOrder(true)

        val state = viewModel.uiState.value
        assertEquals(NoteSet.CUSTOM, state.noteSet)
        assertEquals(listOf(c4, d4), state.activeNotes)
        assertFalse(state.isEditingCustomNotes, "the dialog cannot be opened mid exercise")
        assertEquals(Beats.of(5), state.beatsPerNote)
        assertFalse(state.isRandom)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `reset returns to the setup screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setBeatsPerNote(Beats.of(3))

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.phase is FingeringPhase.Finished)

        viewModel.reset()
        advanceUntilIdle()

        assertEquals(FingeringPhase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `mixed order shuffles the same selection differently`() = runTest(dispatcher) {
        fun run(seed: Int): List<Note> {
            val viewModel = viewModel(seed = seed)
            viewModel.setBeatsPerNote(Beats.of(3))
            viewModel.start()
            advanceUntilIdle()
            val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
            return result.notes.map { it.note }
        }

        val first = run(seed = 1)
        val second = run(seed = 2)

        assertEquals(
            NoteSets.notesOf(NoteSet.BEGINNER, SaxophoneFingerings, emptySet()).toSet(),
            first.toSet(),
        )
        assertTrue(first != second, "two seeds should not give the same order")
    }

    // --- playing the notes ---------------------------------------------------------------------

    /** A trainer with a microphone, one note, and playing switched on. */
    private fun TestScope.playingViewModel(
        microphone: AudioCapture,
        vararg notes: Note,
    ): FingeringViewModel {
        val viewModel = viewModel(microphone = microphone)
        viewModel.selectOnly(*notes)
        viewModel.setRandomOrder(false)
        viewModel.setBeatsPerNote(Beats.of(3))
        viewModel.setCheckPlaying(true)
        return viewModel
    }

    @Test
    fun `the fingering is held back until the note has been played`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = playingViewModel(microphone, c4, d4)

        viewModel.start()
        advanceTimeBy(500)

        val asked = viewModel.uiState.value.progress!!
        assertFalse(asked.showFingering, "the answer would give the exercise away")
        assertEquals(Heard.NOTHING, asked.heard)

        microphone.frequencyHz = soundingFrequencyOf(c4)
        advanceTimeBy(500)

        val played = viewModel.uiState.value.progress!!
        assertTrue(played.showFingering, "the fingering is the reward for playing it")
        assertEquals(Heard.CORRECT, played.heard)
        assertEquals(c4, played.currentNote)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note played correctly is recorded and the exercise moves on`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = playingViewModel(microphone, c4, d4)

        viewModel.start()
        advanceTimeBy(300)
        microphone.frequencyHz = soundingFrequencyOf(c4)
        // Long enough to count as played, plus the time the fingering stays up as the answer.
        advanceTimeBy(2_000)

        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertTrue(result.checked)
        assertEquals(listOf(true), result.notes.map { it.playedCorrectly })
    }

    @Test
    fun `a wrong note is named and the exercise stays where it is`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40)
        val viewModel = playingViewModel(microphone, c4, d4)

        viewModel.start()
        advanceTimeBy(300)
        microphone.frequencyHz = soundingFrequencyOf(e4)
        advanceTimeBy(1_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Heard.WRONG, progress.heard)
        // Named as the player would read it, not as it sounds in the room.
        assertEquals(e4, progress.detectedNote)
        assertEquals(0, progress.noteIndex, "a wrong note does not move the exercise on")
        assertFalse(progress.showFingering, "and it does not give the answer away either")

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a sound with no pitch in it is not a wrong note`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 40, noisy = true)
        val viewModel = playingViewModel(microphone, c4)

        viewModel.start()
        advanceTimeBy(1_000)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(Heard.UNCLEAR, progress.heard)
        assertNull(progress.detectedNote, "nothing was recognised, so nothing is named")

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note nobody manages to play is shown anyway`() = runTest(dispatcher) {
        // Not knowing the grip is exactly when a player needs to be shown it, so the time running
        // out reveals the fingering rather than skipping past it.
        val microphone = FakeMicrophone(frameDurationMs = 40, frequencyHz = null)
        val viewModel = playingViewModel(microphone, c4, d4)

        viewModel.start()
        advanceTimeBy(3_200)

        val progress = viewModel.uiState.value.progress!!
        assertTrue(progress.showFingering)
        assertEquals(0, progress.noteIndex, "still the same note, being shown")

        // The answer stays up for a moment, then the exercise carries on.
        advanceTimeBy(1_300)
        assertEquals(1, viewModel.uiState.value.progress!!.noteIndex)

        viewModel.stop()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertEquals(listOf(false), result.notes.map { it.playedCorrectly })
    }

    @Test
    fun `with playing switched off the fingering is simply shown`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectOnly(c4)
        viewModel.setBeatsPerNote(Beats.of(3))

        viewModel.start()
        advanceTimeBy(500)

        val progress = viewModel.uiState.value.progress!!
        assertTrue(progress.showFingering, "the diagram is the lesson when nobody is listening")
        assertEquals(Heard.NOTHING, progress.heard)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a refused microphone turns checking off and leaves the exercise usable`() =
        runTest(dispatcher) {
            val viewModel = viewModel(microphone = FakeMicrophone(frameDurationMs = 40))
            viewModel.setCheckPlaying(true)

            viewModel.onMicrophoneUnavailable()

            assertFalse(viewModel.uiState.value.checkPlaying)
            assertTrue(viewModel.uiState.value.checkUnavailable)
            assertTrue(viewModel.uiState.value.canStart)
        }

    @Test
    fun `without a microphone the trainer never claims to be listening`() = runTest(dispatcher) {
        val viewModel = viewModel(microphone = null)
        viewModel.selectOnly(c4)
        viewModel.setBeatsPerNote(Beats.of(3))
        viewModel.setCheckPlaying(true)

        viewModel.start()
        advanceUntilIdle()

        val result = (viewModel.uiState.value.phase as FingeringPhase.Finished).result
        assertFalse(result.checked)
        assertEquals(0, result.correctCount)
    }

    // --- note display ------------------------------------------------------------------------

    @Test
    fun `the shared default decides how notes are shown`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplay(NoteDisplay.NAME)

        assertEquals(NoteDisplay.NAME, viewModel(settings = settings).uiState.value.noteDisplay)
    }

    @Test
    fun `this tool can be set differently without touching the default`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)

        viewModel.setNoteDisplay(NoteDisplay.STAFF)
        advanceUntilIdle()

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
        assertEquals(
            NoteDisplay.STAFF,
            settings.settings.value.noteDisplay,
            "the shared default is left alone",
        )
        assertEquals(
            NoteDisplay.STAFF,
            settings.settings.value.noteDisplayFor(TrainingTool.LONG_TONES),
        )
    }

    // --- ready-made exercises ------------------------------------------------------------------

    @Test
    fun `a preset decides the whole exercise`() = runTest(dispatcher) {
        val state = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(8),
                rounds = 2,
            ),
        ).uiState.value

        assertEquals(listOf(c4, d4, e4), state.activeNotes)
        assertEquals(NoteOrder.ORDERED, state.order)
        assertEquals(Beats.of(8), state.beatsPerNote)
        assertEquals(2, state.rounds)
        assertTrue(state.isPreconfigured)
        assertTrue(state.canStart)
    }

    @Test
    fun `a preset cannot ask for a note the chart does not know`() = runTest(dispatcher) {
        // Well below the lowest note a tenor saxophone has.
        val unplayable = Note(NoteName.C, 2)

        val state = viewModel(preset = FingeringPreset(notes = listOf(c4, unplayable))).uiState.value

        assertEquals(listOf(c4), state.activeNotes)
    }

    @Test
    fun `the rounds a preset asks for are all played`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(3),
                rounds = 2,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        assertEquals(4, viewModel.uiState.value.progress!!.noteCount)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `auto start is offered once and then never again`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = FingeringPreset(notes = listOf(c4)))

        assertTrue(viewModel.consumeAutoStart())
        assertFalse(viewModel.consumeAutoStart(), "a rotation must not restart the exercise")
    }

    @Test
    fun `a preset that does not start by itself is never offered`() = runTest(dispatcher) {
        val viewModel = viewModel(preset = FingeringPreset(notes = listOf(c4), autoStart = false))

        assertFalse(viewModel.consumeAutoStart())
        assertTrue(viewModel.uiState.value.phase is FingeringPhase.Idle, "it waits on the Start button")
    }

    @Test
    fun `a freely configured run is never offered an auto start`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertFalse(viewModel.consumeAutoStart())
        assertFalse(viewModel.uiState.value.isPreconfigured)
    }

    @Test
    fun `a preset fixes the presentation without touching the saved preference`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository()
            settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)

            val viewModel = viewModel(
                settings = settings,
                preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
            )
            advanceUntilIdle()

            assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
            assertEquals(
                NoteDisplay.NAME,
                settings.settings.value.noteDisplayFor(TrainingTool.FINGERING),
                "the player's own choice is untouched",
            )
        }

    @Test
    fun `a settings change cannot overwrite what a preset fixed`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(
            settings = settings,
            preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
        )
        advanceUntilIdle()

        settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)
        advanceUntilIdle()

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.noteDisplay)
    }

    @Test
    fun `a preset that says nothing about presentation follows the player`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setNoteDisplayFor(TrainingTool.FINGERING, NoteDisplay.NAME)

        val viewModel = viewModel(settings = settings, preset = FingeringPreset(notes = listOf(c4)))
        advanceUntilIdle()

        assertEquals(NoteDisplay.NAME, viewModel.uiState.value.noteDisplay)
    }

    @Test
    fun `a preset cannot switch on listening without a microphone`() = runTest(dispatcher) {
        val state = viewModel(
            microphone = null,
            preset = FingeringPreset(notes = listOf(c4), checkPlaying = true),
        ).uiState.value

        assertFalse(state.checkPlaying)
    }

    // --- how each note is presented -------------------------------------------------------------

    @Test
    fun `a random presentation is resolved before it reaches the screen`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(3),
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        val progress = viewModel.uiState.value.progress!!
        assertEquals(
            NoteDisplay.RANDOM,
            viewModel.uiState.value.noteDisplay,
            "the setting itself is untouched",
        )
        assertTrue(
            progress.noteDisplay in listOf(NoteDisplay.NAME, NoteDisplay.STAFF),
            "the screen was handed ${progress.noteDisplay}",
        )

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a note keeps its presentation while it is on screen`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(8),
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)
        val first = viewModel.uiState.value.progress!!

        // The progress is republished every tick; the note must not flicker between the two.
        advanceTimeBy(4_000)
        val stillFirst = viewModel.uiState.value.progress!!

        assertEquals(first.currentNote, stillFirst.currentNote)
        assertEquals(first.noteDisplay, stillFirst.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `the preview shows what the next note will actually look like`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(3),
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)
        val promised = viewModel.uiState.value.progress!!.nextNoteDisplay

        advanceTimeBy(3_000)
        val arrived = viewModel.uiState.value.progress!!

        assertEquals(d4, arrived.currentNote)
        assertEquals(promised, arrived.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `both presentations turn up over a long enough exercise`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(
                notes = listOf(c4, d4, e4),
                order = NoteOrder.ORDERED,
                beatsPerNote = Beats.of(3),
                rounds = 8,
                noteDisplay = NoteDisplay.RANDOM,
            ),
        )

        viewModel.start()
        advanceTimeBy(10)

        val seen = mutableSetOf<NoteDisplay>()
        repeat(24) {
            viewModel.uiState.value.progress?.let { seen += it.noteDisplay }
            advanceTimeBy(3_000)
        }

        assertEquals(setOf(NoteDisplay.NAME, NoteDisplay.STAFF), seen)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `a fixed presentation is passed straight through`() = runTest(dispatcher) {
        val viewModel = viewModel(
            preset = FingeringPreset(notes = listOf(c4), noteDisplay = NoteDisplay.STAFF),
        )

        viewModel.start()
        advanceTimeBy(10)

        assertEquals(NoteDisplay.STAFF, viewModel.uiState.value.progress!!.noteDisplay)

        viewModel.stop()
        advanceUntilIdle()
    }

    @Test
    fun `choosing a fixed presentation leaves the note order alone`() = runTest(dispatcher) {
        // The display schedule shares the exercise's generator, so it must not draw from it unless
        // the presentation is actually left to chance.
        fun orderWith(display: NoteDisplay): List<Note> {
            val viewModel = viewModel(
                preset = FingeringPreset(
                    notes = listOf(c4, d4, e4),
                    beatsPerNote = Beats.of(3),
                    rounds = 2,
                    noteDisplay = display,
                ),
            )
            viewModel.start()
            advanceTimeBy(10)
            val notes = mutableListOf<Note>()
            repeat(6) {
                viewModel.uiState.value.progress?.let { notes += it.currentNote }
                advanceTimeBy(3_000)
            }
            viewModel.stop()
            return notes
        }

        assertEquals(orderWith(NoteDisplay.NAME), orderWith(NoteDisplay.STAFF))
        advanceUntilIdle()
    }

    // --- what the notes are called ---------------------------------------------------------------

    @Test
    fun `note names follow the app setting`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        assertEquals("B4", viewModel.uiState.value.nameOf(Note(NoteName.B, 4)))

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals("H4", viewModel.uiState.value.nameOf(Note(NoteName.B, 4)))
    }

    @Test
    fun `the eleventh pitch is a B flat, never an A sharp`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        // The beginner-plus set calls it B flat in its own description; the screen now agrees.
        assertEquals("B♭4", viewModel.uiState.value.nameOf(Note(NoteName.A_SHARP, 4)))

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals("B4", viewModel.uiState.value.nameOf(Note(NoteName.A_SHARP, 4)))
    }

    @Test
    fun `naming changes what is shown and nothing else`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        val before = viewModel.uiState.value.activeNotes

        settings.setNoteNaming(NoteNaming.GERMAN)
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value.activeNotes, "the notes practised are the same pitches")
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
    fun `every choice survives being written down and started again`() = runTest(dispatcher) {
        val microphone = FakeMicrophone(frameDurationMs = 20)
        val configured = viewModel(microphone = microphone)
        configured.selectOnly(c4, d4, e4)
        configured.setRandomOrder(false)
        configured.setBeatsPerNote(Beats.of(3))
        configured.setTempo(96)
        configured.setShowNextNote(false)
        configured.setCheckPlaying(true)
        configured.setSuccessDelaySeconds(2f)
        val written = configured.uiState.value.toSetup()

        val preset = assertNotNull(fingeringPresetOf(written, editable = true))
        val started = viewModel(microphone = microphone, preset = preset)

        assertEquals(written, started.uiState.value.toSetup())
    }

    @Test
    fun `the tempo is part of the exercise, since it is what beats are counted at`() =
        runTest(dispatcher) {
            val configured = viewModel()
            configured.selectOnly(c4, d4)
            configured.setTempo(120)

            val preset = assertNotNull(fingeringPresetOf(configured.uiState.value.toSetup()))

            assertEquals(120, preset.tempo.bpm)
        }

    @Test
    fun `a named set of notes comes back as the notes themselves`() = runTest(dispatcher) {
        val configured = viewModel()
        configured.selectNoteSet(NoteSet.BEGINNER)
        val expected = configured.uiState.value.activeNotes

        val preset = assertNotNull(fingeringPresetOf(configured.uiState.value.toSetup()))

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
    fun `an untouched setup is written down when it is asked for`() = runTest(dispatcher) {
        val setups = InMemoryExerciseSetupRepository()

        viewModel(setups = setups).rememberSetupNow()

        assertFalse(setups.setupOf(Exercise.FINGERING).isEmpty)
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
        val preset = assertNotNull(fingeringPresetOf(ExerciseSetup()))

        assertTrue(preset.notes.isNotEmpty())
    }

}

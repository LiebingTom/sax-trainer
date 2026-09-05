@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.notetrainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.WebAudioCapture
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.testing.FakeMicrophone
import com.example.saxtrainer.core.testing.FakeSettingsRepository
import com.example.saxtrainer.core.testing.ScreenHost
import com.example.saxtrainer.core.testing.SilentMetronome
import com.example.saxtrainer.core.testing.awaitInRealTime
import com.example.saxtrainer.core.testing.measureThroughput
import com.example.saxtrainer.core.testing.runScreenTest
import com.example.saxtrainer.core.testing.shows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The note trainer, running in a browser.
 *
 * This is the trainer with the strictest audio path: it waits for a note to be played and reports
 * how long it took. `NoteTrainerViewModelTest` checks that arithmetic against a virtual clock on
 * both targets; what is checked here is that the same thing happens on wasmJs in real time, with
 * the real gate, the real YIN and the real note lookup between the microphone and the screen.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class NoteTrainerOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { NoteTrainer(host) }

        onNodeWithText("Note Trainer").assertIsDisplayed()
        onNodeWithText("Mixed order").performScrollTo().assertIsDisplayed()
        onNodeWithText("Show the fingering").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun a_preset_arrives_as_a_ready_made_exercise() = runScreenTest { host ->
        setContent { NoteTrainer(host, preset = twoNotes(autoStart = false)) }

        onNodeWithText("Ready").assertIsDisplayed()
        assertFalse(shows("Mixed order"), "a preset offered the setup it was meant to replace")
    }

    @Test
    fun a_note_played_into_the_microphone_is_heard_and_moves_the_exercise_on() = runScreenTest { host ->
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { NoteTrainer(host, twoNotes(), microphone) }

        awaitInRealTime("the first note") { shows("Play it") }

        microphone.frequencyHz = sounding(Note(NoteName.C, 4))
        awaitInRealTime("the first note to be recognised") { shows("Yes") }

        // The exercise moves on by itself once the note has been held long enough. The tone is left
        // sounding until it does: what was right for the first note is wrong for the second, so
        // being told so is how the test knows the exercise advanced rather than guessing at a gap.
        awaitInRealTime("the second note") { shows("That is C4") }

        microphone.frequencyHz = sounding(Note(NoteName.D, 4))
        awaitInRealTime("the exercise to finish") { shows("Note practice complete") }
        onNodeWithText("2 of 2 played").assertIsDisplayed()
    }

    @Test
    fun a_note_that_is_not_the_one_asked_for_is_named_rather_than_accepted() = runScreenTest { host ->
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { NoteTrainer(host, twoNotes(), microphone) }

        awaitInRealTime("the first note") { shows("Play it") }

        microphone.frequencyHz = sounding(Note(NoteName.G, 4))
        awaitInRealTime("the wrong note to be named") { shows("That is G4") }
        assertFalse(shows("Yes"), "a wrong note was accepted")
    }

    @Test
    fun the_browsers_own_microphone_keeps_up_while_a_note_is_waited_for() = runScreenTest { host ->
        // The real chain rather than a fake one: `getUserMedia`, the worklet, the crossing into
        // Kotlin, the gate and YIN, with this screen redrawing on the same thread. Nothing times
        // this trainer out, so it waits for a note it is never played and the audio keeps flowing
        // for exactly as long as the measurement needs.
        val microphone = WebAudioCapture()
        setContent { NoteTrainer(host, twoNotes(), microphone) }

        awaitInRealTime("the first note") { shows("Play it") }

        val throughput = measureThroughput("note trainer", microphone)

        assertEquals(0, throughput.dropped, "the screen starved the microphone")
        assertEquals(0, throughput.poolMisses, "the worklet had to allocate a batch buffer")
        assertTrue(
            throughput.keptUp > 0.9,
            "only ${throughput.keptUp * 100} % of the frames asked for arrived",
        )
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runScreenTest { host ->
        setContent { NoteTrainer(host, preset = twoNotes()) }

        awaitInRealTime("the running exercise") { shows("Stop") }

        host.leaveAndReturn()

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/** Two notes in a fixed order, so a run is short enough to play through in real time. */
private fun twoNotes(autoStart: Boolean = true) = NoteTrainerPreset(
    notes = listOf(Note(NoteName.C, 4), Note(NoteName.D, 4)),
    order = NoteOrder.ORDERED,
    autoStart = autoStart,
)

/** What a microphone hears when [written] is played on the default instrument. */
private fun sounding(written: Note): Double =
    Instrument.TENOR_SAX.transposition.concertFor(written).frequencyHz()

@Composable
private fun NoteTrainer(
    host: ScreenHost,
    preset: NoteTrainerPreset? = null,
    microphone: AudioCapture = FakeMicrophone(frameDurationMs = 10),
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            NoteTrainerRoute(
                settingsRepository = FakeSettingsRepository(),
                audioCapture = microphone,
                newMetronome = { SilentMetronome() },
                onBack = {},
                preset = preset,
            )
        }
    }
}

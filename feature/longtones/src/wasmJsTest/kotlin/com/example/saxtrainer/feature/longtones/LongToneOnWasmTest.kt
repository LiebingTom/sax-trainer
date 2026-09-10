@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.longtones

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
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.settings.AppSettings
import com.example.saxtrainer.core.settings.InMemoryExerciseSetupRepository
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
import kotlin.test.assertTrue

/**
 * Long tones, running in a browser.
 *
 * This is the heaviest trainer per frame - a spectrum and a pitch estimate on every hop, redrawn
 * live - and the browser gives it one thread to do that on. `LongToneViewModelTest` checks what it
 * measures against a virtual clock on both targets; what is checked here is that the whole chain
 * still keeps up on wasmJs in real time: frames from the microphone, through the gate and the
 * analysis, onto a screen that is being redrawn while they arrive.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class LongToneOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { LongTones(host) }

        onNodeWithText("Long Tones").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("$FAST_BPM BPM").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun a_tone_held_into_the_microphone_is_measured_while_it_sounds() = runScreenTest { host ->
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { LongTones(host, microphone) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the count-in") { shows("Get ready") }
        awaitInRealTime("the exercise") { shows("No signal - play louder") }

        // The gate, the level meter and the spectrum are all fed by the same frames.
        microphone.frequencyHz = A4_HZ
        awaitInRealTime("the tone to be heard") { shows("Signal detected") }

        // Held to the end of the note rather than stopped: what finishes it is the exercise's own
        // clock, running in the browser.
        awaitInRealTime("the end of the exercise") { shows("Exercise finished") }
    }

    @Test
    fun silence_is_not_reported_as_a_sound() = runScreenTest { host ->
        val microphone = FakeMicrophone(frameDurationMs = 10)
        setContent { LongTones(host, microphone) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the exercise") { shows("No signal - play louder") }

        // Noise is a sound with no pitch in it. Loud enough to open the gate, so the exercise hears
        // something the whole way through, and it must still not read as a note.
        microphone.noisy = true
        awaitInRealTime("the noise to be heard") { shows("Signal detected") }
        awaitInRealTime("the end of the exercise") { shows("Exercise finished") }
        onNodeWithText("Average pitch: -").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_browsers_own_microphone_keeps_up_with_the_heaviest_trainer() = runScreenTest { host ->
        // Not a fake: the browser's `getUserMedia`, the worklet, the crossing into Kotlin and the
        // real spectrum and pitch analysis, all while this screen is redrawing them. `:core:audio`
        // measures the same chain with nothing on top of it; the question here is what is left of
        // it once the one thread Kotlin/Wasm has is also drawing.
        val microphone = WebAudioCapture()
        // At the trainer's own tempo a note lasts five seconds, which is long enough to average
        // over. The hurried tempo the other cases use would end the exercise mid-measurement.
        setContent { LongTones(host, microphone, bpm = LongToneConfig().defaultTempo.bpm) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the running exercise") { shows("Signal detected") }

        val throughput = measureThroughput("long tones", microphone)

        assertEquals(0, throughput.dropped, "the screen starved the microphone")
        assertEquals(0, throughput.poolMisses, "the worklet had to allocate a batch buffer")
        assertTrue(
            throughput.keptUp > 0.9,
            "only ${throughput.keptUp * 100} % of the frames asked for arrived",
        )
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runScreenTest { host ->
        // At the trainer's own tempo the note lasts five seconds, which is what leaves room to
        // leave in the middle of one. Everywhere else these tests hurry the exercise along.
        setContent { LongTones(host, bpm = LongToneConfig().defaultTempo.bpm) }

        onNodeWithText("Start").performClick()
        awaitInRealTime("the running exercise") { shows("No signal - play louder") }

        host.leaveAndReturn()

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/** Fast enough that a count-in and a held note are a few seconds of a browser test. */
private const val FAST_BPM = 200

/** Concert A, comfortably inside the range the exercise looks for a pitch in. */
private const val A4_HZ = 440.0

@Composable
private fun LongTones(
    host: ScreenHost,
    microphone: AudioCapture = FakeMicrophone(frameDurationMs = 10),
    bpm: Int = FAST_BPM,
) {
    val settings = FakeSettingsRepository(AppSettings(countInClick = false))
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            LongToneRoute(
                audioCapture = microphone,
                newMetronome = { SilentMetronome() },
                settingsRepository = settings,
                exerciseSetups = InMemoryExerciseSetupRepository(),
                onBack = {},
                // The one place this trainer is configured from is its config, so the ViewModel is
                // built here rather than left to the route's default: at the default sixty beats a
                // minute a count-in and a five-beat note are nine seconds of browser test.
                viewModel = LongToneViewModel(
                    audioCapture = microphone,
                    settingsRepository = settings,
                    exerciseSetups = InMemoryExerciseSetupRepository(),
                    metronome = SilentMetronome(),
                    config = LongToneConfig(defaultTempo = Tempo(bpm)),
                ),
            )
        }
    }
}

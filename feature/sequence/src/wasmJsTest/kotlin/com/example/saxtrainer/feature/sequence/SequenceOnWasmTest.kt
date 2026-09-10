@file:OptIn(ExperimentalTestApi::class)

package com.example.saxtrainer.feature.sequence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.WebAudioCapture
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.music.Tempo
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
 * The sequence trainer, running in a browser.
 *
 * A sequence is the one exercise that keeps time by itself: it counts in, walks a written line beat
 * by beat and ends when the line does, with the microphone listening throughout. That whole run is
 * checked against a virtual clock on both targets by `SequenceViewModelTest`; what is checked here
 * is that it also happens on the browser's real clock, in Wasm, at the pace the tempo asks for.
 *
 * Anything the app's own clock has to reach is waited for with `awaitInRealTime` rather than
 * `waitUntil`, which would hold the browser's one thread and stop the very work being waited for.
 */
class SequenceOnWasmTest {

    @Test
    fun the_setup_screen_is_drawn_from_shared_code() = runScreenTest { host ->
        setContent { Sequence(host) }

        onNodeWithText("Sequence Trainer").assertIsDisplayed()
        onNodeWithText("Add inverse").performScrollTo().assertIsDisplayed()
        onNodeWithText("Check pitch").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun a_preset_counts_in_walks_the_line_and_ends_where_the_line_does() = runScreenTest { host ->
        setContent { Sequence(host, preset = quickScale()) }

        // Counted out on the clock, because this preset asked for no click.
        awaitInRealTime("the count-in") { shows("Counting you in") }
        awaitInRealTime("the line") { shows("Stop") }

        // Nothing further is done to it: the exercise ends because the written line ran out.
        awaitInRealTime("the end of the line") { shows("Sequence complete") }
    }

    @Test
    fun the_tempo_the_preset_asked_for_is_the_one_on_screen() = runScreenTest { host ->
        setContent { Sequence(host, preset = quickScale()) }

        awaitInRealTime("the running exercise") { shows("$FAST_BPM BPM") }
    }

    @Test
    fun the_browsers_own_microphone_keeps_up_while_the_line_is_walked() = runScreenTest { host ->
        // The real chain rather than a fake one, under the trainer that also runs a beat clock and
        // re-engraves a staff on every step. The line is walked at a pace that outlasts the
        // measurement rather than at the hurried tempo the other cases use.
        val microphone = WebAudioCapture()
        setContent { Sequence(host, preset = quickScale(bpm = 100), microphone = microphone) }

        awaitInRealTime("the running exercise") { shows("Stop") }

        val throughput = measureThroughput("sequence trainer", microphone)

        assertEquals(0, throughput.dropped, "the screen starved the microphone")
        assertEquals(0, throughput.poolMisses, "the worklet had to allocate a batch buffer")
        assertTrue(
            throughput.keptUp > 0.9,
            "only ${throughput.keptUp * 100} % of the frames asked for arrived",
        )
    }

    @Test
    fun leaving_the_screen_stops_the_exercise() = runScreenTest { host ->
        setContent { Sequence(host, preset = quickScale()) }

        awaitInRealTime("the running exercise") { shows("Stop") }

        host.leaveAndReturn()

        awaitInRealTime("the stopped exercise") { shows("Stopped early") }
    }
}

/**
 * One scale, once through, fast, and silently counted in.
 *
 * The click is off because a real one is the browser's `AudioContext`, which `:core:audio` tests on
 * its own; without it the same count is kept on the clock, which is the path this trainer shares
 * with every other.
 */
private fun quickScale(bpm: Int = FAST_BPM) = SequencePreset(
    sequenceId = SequenceLibrary.cMajorUp.id,
    tempo = Tempo(bpm),
    repeats = 1,
    metronome = false,
    checkPitch = true,
)

/** Fast enough that a whole scale is a few seconds of a browser test. */
private const val FAST_BPM = 240

@Composable
private fun Sequence(
    host: ScreenHost,
    preset: SequencePreset? = null,
    microphone: AudioCapture = FakeMicrophone(frameDurationMs = 10),
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides host,
        LocalLifecycleOwner provides host,
    ) {
        SaxTrainerTheme(darkTheme = false) {
            SequenceRoute(
                settingsRepository = FakeSettingsRepository(),
                exerciseSetups = InMemoryExerciseSetupRepository(),
                audioCapture = microphone,
                newMetronome = { SilentMetronome() },
                onBack = {},
                preset = preset,
            )
        }
    }
}

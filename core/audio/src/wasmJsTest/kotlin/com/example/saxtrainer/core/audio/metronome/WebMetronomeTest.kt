package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.audio.capture.inBrowser
import com.example.saxtrainer.core.audio.capture.waitUntil
import com.example.saxtrainer.core.music.Tempo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.js.Promise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The browser's metronome, against a real `AudioContext`.
 *
 * What is being tested is the contract the rest of the app leans on, not the sound: a beat appears
 * in `state` once it has been *played*, the beats keep their place in the bar, and `clickCountIn` -
 * shared, unmodified, written for the Android engine - works against this one too.
 */
class WebMetronomeTest {

    @Test
    fun beats_are_published_as_they_are_played_and_keep_their_place_in_the_bar(): Promise<JsAny?> =
        inBrowser {
            val metronome = WebMetronome()
            val seen = mutableListOf<Int>()
            var lastCounter = 0L
            try {
                metronome.setBpm(FAST_BPM)
                metronome.start()

                waitUntil("the first beat") { metronome.state.value.beatCounter > 0 }
                while (seen.size < 8) {
                    val state = metronome.state.value
                    if (state.beatCounter > lastCounter) {
                        lastCounter = state.beatCounter
                        seen += state.beatInBar
                    }
                    delay(5)
                }
            } finally {
                metronome.stop()
            }

            assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), seen, "the bar did not come round")
            assertTrue(metronome.sampleRate > 0, "no sample rate was read from the context")
        }

    @Test
    fun the_beats_keep_the_tempo_they_were_given(): Promise<JsAny?> = inBrowser {
        val metronome = WebMetronome()
        try {
            metronome.setBpm(FAST_BPM)
            metronome.start()
            waitUntil("the first beat") { metronome.state.value.beatCounter > 0 }

            val startedAt = currentMillis()
            val from = metronome.state.value.beatCounter
            waitUntil("eight beats") { metronome.state.value.beatCounter >= from + 8 }
            val perBeat = (currentMillis() - startedAt) / 8.0

            val expected = 60_000.0 / FAST_BPM
            // Beats are published on a 25 ms scheduler tick, so a published beat is late by up to
            // that much; the interval between them is not, and it is the interval that keeps time.
            assertTrue(
                abs(perBeat - expected) < expected * 0.15,
                "beats came $perBeat ms apart where $FAST_BPM bpm is $expected ms",
            )
        } finally {
            metronome.stop()
        }
    }

    @Test
    fun stopping_stops_the_beats(): Promise<JsAny?> = inBrowser {
        val metronome = WebMetronome()
        metronome.setBpm(FAST_BPM)
        metronome.start()
        waitUntil("the first beat") { metronome.state.value.beatCounter > 0 }

        metronome.stop()
        val afterStop = metronome.state.value.beatCounter
        delay(600)

        assertEquals(afterStop, metronome.state.value.beatCounter, "beats kept coming after stop")
        assertEquals(false, metronome.state.value.isRunning)
    }

    @Test
    fun a_stopped_metronome_can_be_started_again(): Promise<JsAny?> = inBrowser {
        val metronome = WebMetronome()
        try {
            metronome.setBpm(FAST_BPM)
            metronome.start()
            waitUntil("the first beat") { metronome.state.value.beatCounter > 0 }
            metronome.release()

            val before = metronome.state.value.beatCounter
            metronome.start()
            waitUntil("a beat after restarting") { metronome.state.value.beatCounter > before }
        } finally {
            metronome.stop()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun the_shared_count_in_counts_on_this_metronomes_clicks(): Promise<JsAny?> = inBrowser {
        // `clickCountIn` is shared code written against the Android engine's published beats. It has
        // to work here untouched, and the beat it returns on is the one the exercise starts from.
        val metronome = WebMetronome()
        val counted = mutableListOf<Int>()
        var finished = false
        try {
            val counting = GlobalScope.launch {
                metronome.clickCountIn(beats = 4, tempo = Tempo(FAST_BPM)) { counted += it }
                finished = true
            }
            waitUntil("the count-in to finish") { finished }
            counting.join()
        } finally {
            metronome.stop()
        }

        assertEquals(listOf(4, 3, 2, 1), counted)
        assertTrue(
            metronome.state.value.beatCounter >= 5,
            "the count-in returned before the beat the exercise starts on",
        )
    }

    private fun currentMillis(): Double =
        com.example.saxtrainer.core.audio.web.performanceNow()

    private companion object {
        /** Fast enough that a case takes seconds, slow enough to stay inside the supported range. */
        const val FAST_BPM = 240
    }
}

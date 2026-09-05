package com.example.saxtrainer.core.audio.metronome

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The click both platforms play.
 *
 * Android writes these shorts straight to an AudioTrack and the browser copies them into an
 * AudioBuffer, so anything wrong here is wrong in the same way on both.
 */
class ClickSynthTest {

    @Test
    fun `the click lasts the same time whatever the rate the platform runs at`() {
        // The browser picks its own rate and Android is given one; a click of a fixed sample count
        // would be a different sound on each.
        val at48k = ClickSynth(48_000).normalClick.size
        val at44k = ClickSynth(44_100).normalClick.size
        val at24k = ClickSynth(24_000).normalClick.size

        assertEquals(48_000 / at48k, 44_100 / at44k, "the click is not the same length in seconds")
        assertEquals(48_000 / at48k, 24_000 / at24k, "the click is not the same length in seconds")
    }

    @Test
    fun `the downbeat is louder than the beats after it`() {
        val clicks = ClickSynth(48_000)

        val accent = clicks.accentClick.maxOf { abs(it.toInt()) }
        val normal = clicks.normalClick.maxOf { abs(it.toInt()) }

        assertTrue(accent > normal, "accent peaked at $accent, normal at $normal")
    }

    @Test
    fun `the click decays instead of stopping dead`() {
        // A click that ended at full amplitude would be a click plus a second click.
        val click = ClickSynth(48_000).normalClick
        val head = click.take(click.size / 4).maxOf { abs(it.toInt()) }
        val tail = click.takeLast(click.size / 4).maxOf { abs(it.toInt()) }

        assertTrue(tail * 4 < head, "head peaked at $head and the tail still at $tail")
    }

    @Test
    fun `nothing is left to chance between one render and the next`() {
        // Rendered once per start on both platforms; two starts must sound the same.
        assertTrue(ClickSynth(48_000).accentClick.contentEquals(ClickSynth(48_000).accentClick))
    }
}

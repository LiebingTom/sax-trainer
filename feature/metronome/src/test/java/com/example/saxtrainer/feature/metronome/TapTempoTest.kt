package com.example.saxtrainer.feature.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {

    private val oneSecond = 1_000_000_000L

    @Test
    fun `first tap cannot produce a tempo`() {
        assertNull(TapTempo().tap(0))
    }

    @Test
    fun `two taps one second apart give 60 bpm`() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        assertEquals(60, tapTempo.tap(oneSecond))
    }

    @Test
    fun `tempo is averaged over recent taps`() {
        val tapTempo = TapTempo()
        var now = 0L
        val interval = oneSecond / 2 // 120 bpm
        tapTempo.tap(now)
        repeat(4) {
            now += interval
            tapTempo.tap(now)
        }
        assertEquals(120, tapTempo.tap(now + interval))
    }

    @Test
    fun `a long pause starts a new measurement`() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(oneSecond / 2)
        assertNull("gap above the reset time discards history", tapTempo.tap(oneSecond * 5))
    }

    @Test
    fun `tempo is clamped to the supported range`() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        // 10 ms apart would be 6000 bpm.
        assertEquals(240, tapTempo.tap(10_000_000L))

        val slow = TapTempo()
        slow.tap(0)
        // 1.9 s apart is below 40 bpm but still within the reset window.
        assertEquals(40, slow.tap(1_900_000_000L))
    }
}

package com.example.saxtrainer.feature.reedlogbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone

/** How the logbook writes a day, a time and a length. */
class ReedTextTest {

    // A fixed offset rather than a named zone: the same answer on every platform, with no time zone
    // database needed to get it.
    private val berlin = UtcOffset(hours = 2).asTimeZone()
    private val afternoon = Instant.parse("2026-10-04T12:02:00Z")

    @Test
    fun `a day and a time are the player's own`() {
        assertEquals("Sun 4 Oct 2026", ReedText.dayOf(afternoon, berlin))
        assertEquals("14:02", ReedText.timeOf(afternoon, berlin))
        assertEquals("2026-10-04", ReedText.isoDayOf(afternoon, berlin))
    }

    @Test
    fun `a length reads in minutes, then hours`() {
        assertEquals("under a minute", ReedText.lengthOf(40.seconds))
        assertEquals("42 min", ReedText.lengthOf(42.minutes))
        assertEquals("1 h 05 min", ReedText.lengthOf(1.hours + 5.minutes))
    }

    @Test
    fun `a running clock counts to the second`() {
        assertEquals("0:42:13", ReedText.clockOf(42.minutes + 13.seconds))
        assertEquals("2:00:00", ReedText.clockOf(2.hours))
    }

    @Test
    fun `playing time reads in minutes, hours and minutes, then whole hours`() {
        assertEquals("Not played yet", ReedText.playedOf(0.seconds))
        assertEquals("35 min played", ReedText.playedOf(35.minutes))
        assertEquals("2 h 05 min played", ReedText.playedOf(2.hours + 5.minutes))
        assertEquals("14 h played", ReedText.playedOf(14.hours + 20.minutes))
    }
}

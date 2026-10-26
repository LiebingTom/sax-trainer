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

    @Test
    fun `a price is read however it is typed, and written back in euros`() {
        assertEquals(450L, ReedText.parsePrice("4.50"))
        assertEquals(450L, ReedText.parsePrice("4,5"))
        assertEquals(400L, ReedText.parsePrice(" € 4 "))
        assertEquals(null, ReedText.parsePrice(""))
        assertEquals(null, ReedText.parsePrice("4.555"))
        assertEquals(null, ReedText.parsePrice("cheap"))
        assertEquals("€4.50", ReedText.priceOf(450))
    }

    @Test
    fun `a length is read in whole minutes, from one to twelve hours`() {
        assertEquals(45, ReedText.parseMinutes("45"))
        assertEquals(30, ReedText.parseMinutes(" 30 "))
        assertEquals(720, ReedText.parseMinutes("720"))
        assertEquals(null, ReedText.parseMinutes("0"))
        assertEquals(null, ReedText.parseMinutes("721"))
        assertEquals(null, ReedText.parseMinutes(""))
        assertEquals(null, ReedText.parseMinutes("abc"))
        assertEquals(null, ReedText.parseMinutes("4.5"))
    }
}

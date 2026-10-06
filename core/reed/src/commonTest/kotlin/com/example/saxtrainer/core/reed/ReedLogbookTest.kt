package com.example.saxtrainer.core.reed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The rules of the logbook: one number per reed in rotation, one session at a time. */
class ReedLogbookTest {

    private val t0 = Instant.fromEpochMilliseconds(1_790_000_000_000)

    private fun ReedLogbook.withReed(id: String, number: Int, at: Instant = t0) =
        plusReed(id, number, "daddario", "daddario-royal", "2.5", at)

    private val threeReeds = ReedLogbook()
        .withReed("a", 1, t0)
        .withReed("b", 2, t0 + 1.minutes)
        .withReed("c", 3, t0 + 2.minutes)

    @Test
    fun `a new reed takes the lowest free number`() {
        assertEquals(1, ReedLogbook().nextFreeNumber())
        assertEquals(4, threeReeds.nextFreeNumber())
        assertEquals(listOf(4), threeReeds.freeNumbers())
    }

    @Test
    fun `a number held by a reed in rotation is refused`() {
        assertSame(threeReeds, threeReeds.withReed("d", 2))
        assertSame(threeReeds, threeReeds.renumbered("c", 1))
    }

    @Test
    fun `a reed put aside frees its number for the next one`() {
        val broken = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0 + 10.minutes)

        assertEquals(listOf(1), broken.freeNumbers(), "the gap, and nothing past the reeds in rotation")
        val replaced = broken.withReed("d", 1, t0 + 11.minutes)

        assertEquals(listOf("d", "b", "c"), replaced.activeReeds.map { it.id })
        assertEquals(1, replaced.reedOf("a")?.number, "the reed put aside keeps the number it had")
        assertEquals(ReedStatus.INACTIVE, replaced.reedOf("a")?.status)
        assertEquals(t0 + 10.minutes, replaced.reedOf("a")?.statusChangedAt)
    }

    @Test
    fun `sessions stay with the reed they were played on when its number is reused`() {
        var logbook = ReedLogbook().withReed("a", 1)
        logbook = logbook.started("s1", "a", t0).stopped(t0 + 30.minutes)
        logbook = logbook.started("s2", "a", t0 + 60.minutes).stopped(t0 + 90.minutes)
        logbook = logbook.withStatus("a", ReedStatus.INACTIVE, t0 + 100.minutes)
        logbook = logbook.withReed("b", 1, t0 + 101.minutes)
        logbook = logbook.started("s3", "b", t0 + 120.minutes).stopped(t0 + 130.minutes)

        assertEquals(listOf("s2", "s1"), logbook.sessionsOf("a").map { it.id })
        assertEquals(listOf("s3"), logbook.sessionsOf("b").map { it.id })
    }

    @Test
    fun `a reed put aside comes back under its old number while it is free`() {
        val aside = threeReeds.withStatus("b", ReedStatus.INACTIVE, t0 + 5.minutes)

        val back = aside.withStatus("b", ReedStatus.ACTIVE, t0 + 6.minutes)

        assertEquals(2, back.reedOf("b")?.number)
    }

    @Test
    fun `a reed put aside comes back under the lowest free number when its own is taken`() {
        val aside = threeReeds.withStatus("b", ReedStatus.INACTIVE, t0 + 5.minutes)
        val taken = aside.withReed("d", 2, t0 + 6.minutes)

        val back = taken.withStatus("b", ReedStatus.ACTIVE, t0 + 7.minutes)

        assertEquals(4, back.reedOf("b")?.number)
        assertEquals(listOf(1, 2, 3, 4), back.activeReeds.map { it.number })
    }

    @Test
    fun `a reed whose number was taken comes back under the number chosen for it`() {
        val aside = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0)
        val taken = aside.withReed("d", 1, t0 + 1.minutes)
        assertTrue(taken.needsNumberToReturn("a"))
        assertTrue(!aside.needsNumberToReturn("a"), "its own number is still free there")

        val back = taken.withStatus("a", ReedStatus.ACTIVE, t0 + 2.minutes, number = 4)

        assertEquals(4, back.reedOf("a")?.number)
        assertEquals(1, back.reedOf("d")?.number, "the reed that took the number keeps it")
    }

    @Test
    fun `a deleted reed takes its sessions and its selection with it`() {
        val played = threeReeds.started("s1", "a", t0).stopped(t0 + 5.minutes)
            .started("s2", "b", t0 + 6.minutes).stopped(t0 + 7.minutes)
            .selected("a")

        val deleted = played.minusReed("a")

        assertNull(deleted.reedOf("a"))
        assertEquals(listOf("s2"), deleted.sessions.map { it.id })
        assertEquals("b", deleted.effectiveSelection()?.id)
    }

    @Test
    fun `played time adds up every session, a running one up to now`() {
        val logbook = threeReeds.started("s1", "a", t0).stopped(t0 + 30.minutes)
            .started("s2", "a", t0 + 60.minutes)

        assertEquals(45.minutes, logbook.playedTime("a", now = t0 + 75.minutes))
        assertEquals(0.minutes, logbook.playedTime("b", now = t0 + 75.minutes))
    }

    @Test
    fun `only one session runs at a time`() {
        val running = threeReeds.started("s1", "a", t0)

        assertSame(running, running.started("s2", "b", t0 + 1.minutes))
        assertEquals("s1", running.runningSession?.id)
        assertTrue(!running.canStart)
    }

    @Test
    fun `a session only starts on a reed in rotation`() {
        val aside = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0)

        assertSame(aside, aside.started("s1", "a", t0 + 1.minutes))
    }

    @Test
    fun `retiring the reed being played stops its session there and then`() {
        val running = threeReeds.started("s1", "a", t0)

        val broken = running.withStatus("a", ReedStatus.INACTIVE, t0 + 20.minutes)

        assertNull(broken.runningSession)
        assertEquals(20.minutes, broken.sessionsOf("a").single().durationAt(t0 + 99.minutes))
    }

    @Test
    fun `a running session lasts until now and a stopped one until its end`() {
        val running = threeReeds.started("s1", "a", t0)
        assertEquals(42.minutes, running.runningSession?.durationAt(t0 + 42.minutes))

        val stopped = running.stopped(t0 + 50.minutes)
        assertEquals(50.minutes, stopped.sessions.single().durationAt(t0 + 99.minutes))
    }

    @Test
    fun `a clock gone backwards ends a session where it began`() {
        val stopped = threeReeds.started("s1", "a", t0).stopped(t0 - 5.minutes)

        assertEquals(t0, stopped.sessions.single().end)
    }

    @Test
    fun `the last reed chosen stays selected while it is in rotation`() {
        val chosen = threeReeds.selected("b")
        assertEquals("b", chosen.effectiveSelection()?.id)

        val aside = chosen.withStatus("b", ReedStatus.INACTIVE, t0)
        assertEquals("a", aside.effectiveSelection()?.id, "falls back to the lowest number")

        assertNull(ReedLogbook().effectiveSelection())
    }

    @Test
    fun `the reed being played is the selection, and it cannot be changed meanwhile`() {
        val running = threeReeds.selected("a").started("s1", "c", t0)

        assertEquals("c", running.effectiveSelection()?.id)
        assertSame(running, running.selected("b"))
    }

    @Test
    fun `a deleted session is gone for good`() {
        val logbook = threeReeds.started("s1", "a", t0).stopped(t0 + 1.minutes)

        assertTrue(logbook.minusSession("s1").sessions.isEmpty())
    }

    @Test
    fun `history lists every reed, the newest first`() {
        val logbook = threeReeds.withStatus("a", ReedStatus.INACTIVE, t0 + 5.minutes)

        assertEquals(listOf("c", "b", "a"), logbook.reedsNewestFirst.map { it.id })
    }
}

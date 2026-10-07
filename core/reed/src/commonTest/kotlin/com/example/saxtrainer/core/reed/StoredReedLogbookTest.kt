package com.example.saxtrainer.core.reed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The logbook written down and read back, including everything it might be handed instead. */
class StoredReedLogbookTest {

    private val t0 = Instant.fromEpochMilliseconds(1_790_000_000_000)

    @Test
    fun `a logbook survives being written down and read back`() {
        val logbook = ReedLogbook()
            .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0, priceCents = 450)
            .plusReed("b", 2, "vandoren", "vandoren-java", "3", t0 + 1.minutes)
            .started("s1", "a", t0 + 2.minutes)
            .stopped(t0 + 30.minutes)
            .withStatus("a", ReedStatus.INACTIVE, t0 + 31.minutes)
            .started("s2", "b", t0 + 40.minutes)

        val restored = StoredReedLogbook.decode(StoredReedLogbook.encode(logbook))

        assertEquals(logbook, restored)
        assertEquals("s2", restored.runningSession?.id, "a running session is still running")
    }

    @Test
    fun `nothing stored is an empty logbook`() {
        assertEquals(ReedLogbook(), StoredReedLogbook.decode(null))
        assertEquals(ReedLogbook(), StoredReedLogbook.decode(""))
    }

    @Test
    fun `lines it cannot read are skipped and the rest is kept`() {
        val stored = listOf(
            "R|a|1|daddario|daddario-royal|2.5|ACTIVE|1790000000000|",
            "R|b|two|daddario|daddario-royal|2.5|ACTIVE|1790000000000|",
            "R|c|2|daddario|daddario-royal|2.5|MELTED|1790000000000|",
            "Q|something a newer version writes",
            "S|s1|a|1790000000000|1790000060000|an extra field",
            "S|s2|a|later|",
        ).joinToString("\n")

        val logbook = StoredReedLogbook.decode(stored)

        assertEquals(listOf("a"), logbook.reeds.map { it.id })
        assertEquals(listOf("s1"), logbook.sessions.map { it.id })
    }

    @Test
    fun `a session whose reed is missing is dropped`() {
        val stored = "R|a|1|daddario|daddario-royal|2.5|ACTIVE|1790000000000|\nS|s1|gone|1790000000000|1790000060000"

        assertTrue(StoredReedLogbook.decode(stored).sessions.isEmpty())
    }

    @Test
    fun `two reeds in rotation under one number are told apart again`() {
        val stored = listOf(
            "R|a|1|daddario|daddario-royal|2.5|ACTIVE|1790000000000|",
            "R|b|1|daddario|daddario-royal|3|ACTIVE|1790000060000|",
            "R|c|1|daddario|daddario-royal|3|BROKEN|1780000000000|1785000000000",
        ).joinToString("\n")

        val logbook = StoredReedLogbook.decode(stored)

        assertEquals(1, logbook.reedOf("a")?.number, "the older one keeps its number")
        assertEquals(2, logbook.reedOf("b")?.number, "the later one moves to a free one")
        assertEquals(1, logbook.reedOf("c")?.number, "a retired reed may share a number")
    }

    @Test
    fun `a reed stored as broken reads as put aside`() {
        val stored = "R|c|1|daddario|daddario-royal|3|BROKEN|1780000000000|1785000000000"

        assertEquals(ReedStatus.INACTIVE, StoredReedLogbook.decode(stored).reedOf("c")?.status)
    }

    @Test
    fun `a selection of a reed that is not there is forgotten`() {
        assertNull(StoredReedLogbook.decode("X|gone").selectedReedId)
    }

    @Test
    fun `the characters the format uses cannot break a line`() {
        val logbook = ReedLogbook().plusReed("a", 1, "daddario", "daddario-royal", "2|5\nH", t0)

        val restored = StoredReedLogbook.decode(StoredReedLogbook.encode(logbook))

        assertEquals("2 5 H", restored.reedOf("a")?.strength)
    }

    @Test
    fun `a reed written before there was a price keeps everything, its sessions too`() {
        // Exactly what the first version wrote: no price field at the end of the reed.
        val stored = listOf(
            "R|a|1|daddario|daddario-royal|2.5|ACTIVE|1790000000000|",
            "S|s1|a|1790000000000|1790000060000",
            "X|a",
        ).joinToString("\n")

        val logbook = StoredReedLogbook.decode(stored)

        assertNull(logbook.reedOf("a")?.priceCents)
        assertEquals(listOf("s1"), logbook.sessionsOf("a").map { it.id })
        assertEquals("a", logbook.selectedReedId)
    }
}

package com.example.saxtrainer.core.reed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The export: one row per session, every reed in it, readable by any spreadsheet. */
class ReedLogbookCsvTest {

    private val t0 = Instant.parse("2026-10-05T14:00:00Z")

    private fun rowsOf(logbook: ReedLogbook): List<String> =
        ReedLogbookCsv.of(logbook).removeSuffix("\r\n").split("\r\n")

    @Test
    fun `an empty logbook is the header alone`() {
        assertEquals(listOf(ReedLogbookCsv.HEADER.joinToString(",")), rowsOf(ReedLogbook()))
    }

    @Test
    fun `every session is a row of its own beside its reed, and a reed never played still appears`() {
        val logbook = ReedLogbook()
            .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
            .plusReed("b", 2, "vandoren", "vandoren-java", "3", t0 + 1.minutes)
            .started("s1", "a", t0 + 10.minutes)
            .stopped(t0 + 40.minutes)
            .withStatus("a", ReedStatus.INACTIVE, t0 + 50.minutes)

        val rows = rowsOf(logbook)

        assertEquals(
            listOf(
                ReedLogbookCsv.HEADER.joinToString(","),
                "a,1,daddario,D'Addario,daddario-royal,Royal,2.5,INACTIVE,2026-10-05T14:00:00Z," +
                    "2026-10-05T14:50:00Z,s1,2026-10-05T14:10:00Z,2026-10-05T14:40:00Z,1800",
                "b,2,vandoren,Vandoren,vandoren-java,Java,3,ACTIVE,2026-10-05T14:01:00Z,,,,,",
            ),
            rows,
        )
    }

    @Test
    fun `a running session has no end and no duration yet`() {
        val logbook = ReedLogbook()
            .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
            .started("s1", "a", t0 + 10.minutes)

        assertEquals(
            "a,1,daddario,D'Addario,daddario-royal,Royal,2.5,ACTIVE,2026-10-05T14:00:00Z,,s1,2026-10-05T14:10:00Z,,",
            rowsOf(logbook)[1],
        )
    }

    @Test
    fun `a field holding a comma or a quote is quoted`() {
        val logbook = ReedLogbook().plusReed("a", 1, "someone, else", "a \"special\" cut", "3", t0)

        val row = rowsOf(logbook)[1]

        assertEquals(
            "a,1,\"someone, else\",\"someone, else\",\"a \"\"special\"\" cut\",\"a \"\"special\"\" cut\",3,ACTIVE," +
                "2026-10-05T14:00:00Z,,,,,",
            row,
        )
    }
}

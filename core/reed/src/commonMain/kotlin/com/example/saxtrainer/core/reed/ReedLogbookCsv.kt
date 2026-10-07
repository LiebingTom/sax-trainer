package com.example.saxtrainer.core.reed

import kotlin.time.Instant

/**
 * The whole logbook as one CSV file, for a spreadsheet or a script.
 *
 * One row per session, with its reed's columns repeated on every row: the shape a spreadsheet's
 * pivot table, pandas or R group by without a join. A reed never played still gets one row, its
 * session columns empty, so every reed is in the file. Reed and session are tied by `reed_id`, not
 * by the number, which a later reed may carry too.
 *
 * Times are UTC in ISO 8601, so the file means the same wherever it is opened; a running session
 * has no end and no duration yet. Fields are quoted as RFC 4180 has it, only where they need it.
 */
object ReedLogbookCsv {

    val HEADER: List<String> = listOf(
        "reed_id",
        "reed_number",
        "manufacturer_id",
        "manufacturer",
        "product_id",
        "product",
        "strength",
        "price_eur",
        "status",
        "reed_created_at",
        "reed_status_changed_at",
        "session_id",
        "session_start",
        "session_end",
        "session_duration_seconds",
    )

    /** The file's text: the header, then reeds oldest first and each reed's sessions in order. */
    fun of(logbook: ReedLogbook): String {
        val rows = mutableListOf(HEADER)
        logbook.reeds.sortedWith(compareBy<Reed> { it.createdAt }.thenBy { it.id }).forEach { reed ->
            val sessions = logbook.sessions.filter { it.reedId == reed.id }.sortedBy { it.start }
            if (sessions.isEmpty()) {
                rows += reedColumns(reed) + List(4) { "" }
            } else {
                sessions.forEach { rows += reedColumns(reed) + sessionColumns(it) }
            }
        }
        return rows.joinToString(LINE_END, postfix = LINE_END) { row -> row.joinToString(",") { quoted(it) } }
    }

    /** `reed-logbook-2026-10-05.csv`, dated [today] as the player's calendar has it. */
    fun fileNameFor(today: String): String = "reed-logbook-$today.csv"

    private fun reedColumns(reed: Reed): List<String> = listOf(
        reed.id,
        reed.number.toString(),
        reed.manufacturerId,
        ReedCatalog.manufacturerNameOf(reed.manufacturerId),
        reed.productId,
        ReedCatalog.productNameOf(reed.productId),
        reed.strength,
        reed.priceCents?.let(::euros).orEmpty(),
        reed.status.name,
        timeOf(reed.createdAt),
        reed.statusChangedAt?.let(::timeOf).orEmpty(),
    )

    private fun sessionColumns(session: ReedSession): List<String> = listOf(
        session.id,
        timeOf(session.start),
        session.end?.let(::timeOf).orEmpty(),
        session.end?.let { (it - session.start).inWholeSeconds.toString() }.orEmpty(),
    )

    private fun timeOf(instant: Instant): String = instant.toString()

    /** "4.50" - a plain decimal with a point, which every spreadsheet and script reads as a number. */
    private fun euros(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    private fun quoted(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /** RFC 4180's line ending, which every spreadsheet reads. */
    private const val LINE_END = "\r\n"
}

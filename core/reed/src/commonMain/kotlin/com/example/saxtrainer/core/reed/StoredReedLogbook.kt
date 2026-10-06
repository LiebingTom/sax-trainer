package com.example.saxtrainer.core.reed

import kotlin.time.Instant

/**
 * What the reed logbook is stored under and how it is written down.
 *
 * The same kind of format as the lesson book's: shared by every platform because storage is per
 * platform and the format must not be, one stored string for the whole logbook, one record per
 * line, fields separated by `|`:
 *
 * ```
 * R|<id>|<number>|<manufacturerId>|<productId>|<strength>|<STATUS>|<createdAt ms>|<statusChangedAt ms or empty>
 * S|<id>|<reedId>|<start ms>|<end ms or empty>
 * X|<selectedReedId>
 * ```
 *
 * Both directions are total. A line that does not parse - including a record, or a field at the
 * end of one, that a newer version added - is skipped or ignored, so a logbook written by another
 * version reads as the parts this one understands. A session whose reed is not there is dropped,
 * since nothing could show it; and should two reeds in rotation turn up with one number, the later
 * one is given a free number rather than either being lost.
 */
internal object StoredReedLogbook {

    /** One key for the whole logbook. */
    const val KEY = "reed_logbook"

    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "|"

    private const val REED = "R"
    private const val SESSION = "S"
    private const val SELECTION = "X"

    fun encode(logbook: ReedLogbook): String {
        val lines = mutableListOf<String>()
        logbook.reeds.forEach { reed ->
            lines += listOf(
                REED,
                reed.id,
                reed.number.toString(),
                reed.manufacturerId,
                reed.productId,
                reed.strength,
                reed.status.name,
                reed.createdAt.toEpochMilliseconds().toString(),
                reed.statusChangedAt?.toEpochMilliseconds()?.toString().orEmpty(),
            ).joinToString(FIELD_SEPARATOR) { it.storable() }
        }
        logbook.sessions.forEach { session ->
            lines += listOf(
                SESSION,
                session.id,
                session.reedId,
                session.start.toEpochMilliseconds().toString(),
                session.end?.toEpochMilliseconds()?.toString().orEmpty(),
            ).joinToString(FIELD_SEPARATOR) { it.storable() }
        }
        logbook.selectedReedId?.let { lines += listOf(SELECTION, it.storable()).joinToString(FIELD_SEPARATOR) }
        return lines.joinToString(LINE_SEPARATOR)
    }

    fun decode(stored: String?): ReedLogbook {
        if (stored.isNullOrEmpty()) return ReedLogbook()

        val reeds = mutableListOf<Reed>()
        val sessions = mutableListOf<ReedSession>()
        var selected: String? = null

        stored.split(LINE_SEPARATOR).forEach { line ->
            val fields = line.split(FIELD_SEPARATOR)
            when (fields.firstOrNull()) {
                REED -> reedOf(fields)?.let { reed -> if (reeds.none { it.id == reed.id }) reeds += reed }
                SESSION -> sessionOf(fields)?.let { session -> if (sessions.none { it.id == session.id }) sessions += session }
                SELECTION -> fields.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { selected = it }
                else -> Unit
            }
        }

        val known = reeds.map { it.id }.toSet()
        return ReedLogbook(
            reeds = withUniqueActiveNumbers(reeds),
            sessions = onlyOneRunning(sessions.filter { it.reedId in known }),
            selectedReedId = selected?.takeIf { it in known },
        )
    }

    private fun reedOf(fields: List<String>): Reed? {
        if (fields.size < 9) return null
        val id = fields[1].takeIf { it.isNotEmpty() } ?: return null
        val number = fields[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val status = statusOf(fields[6]) ?: return null
        val createdAt = instantOf(fields[7]) ?: return null
        return Reed(
            id = id,
            number = number,
            manufacturerId = fields[3],
            productId = fields[4],
            strength = fields[5],
            status = status,
            createdAt = createdAt,
            statusChangedAt = instantOf(fields[8]),
        )
    }

    /** A stored status. `BROKEN`, written before a broken reed was simply put aside, reads as that. */
    private fun statusOf(field: String): ReedStatus? =
        if (field == RETIRED_BROKEN) ReedStatus.INACTIVE else ReedStatus.entries.firstOrNull { it.name == field }

    private const val RETIRED_BROKEN = "BROKEN"

    private fun sessionOf(fields: List<String>): ReedSession? {
        if (fields.size < 5) return null
        val id = fields[1].takeIf { it.isNotEmpty() } ?: return null
        val reedId = fields[2].takeIf { it.isNotEmpty() } ?: return null
        val start = instantOf(fields[3]) ?: return null
        val end = instantOf(fields[4])
        if (end != null && end < start) return null
        return ReedSession(id = id, reedId = reedId, start = start, end = end)
    }

    private fun instantOf(field: String): Instant? = field.toLongOrNull()?.let(Instant::fromEpochMilliseconds)

    /** Two reeds in rotation under one number: the one added later moves to a free number. */
    private fun withUniqueActiveNumbers(reeds: List<Reed>): List<Reed> {
        val taken = mutableSetOf<Int>()
        val renumbered = mutableMapOf<String, Int>()
        reeds.filter { it.isActive }.sortedBy { it.createdAt }.forEach { reed ->
            val number = if (reed.number in taken) generateSequence(1) { it + 1 }.first { it !in taken } else reed.number
            taken += number
            if (number != reed.number) renumbered[reed.id] = number
        }
        return reeds.map { reed -> renumbered[reed.id]?.let { reed.copy(number = it) } ?: reed }
    }

    /**
     * More than one session without an end cannot happen through the rules; should a stored logbook
     * hold that anyway, the latest keeps running and the others end where they began, so no time is
     * invented for them.
     */
    private fun onlyOneRunning(sessions: List<ReedSession>): List<ReedSession> {
        val latestRunning = sessions.filter { it.isRunning }.maxByOrNull { it.start } ?: return sessions
        return sessions.map { if (it.isRunning && it.id != latestRunning.id) it.copy(end = it.start) else it }
    }

    /** A field as it can be written down: the two characters the format itself uses, gone. */
    private fun String.storable(): String = replace(LINE_SEPARATOR, " ").replace(FIELD_SEPARATOR, " ")
}

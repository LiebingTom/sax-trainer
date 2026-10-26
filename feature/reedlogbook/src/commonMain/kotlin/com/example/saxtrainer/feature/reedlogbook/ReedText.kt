package com.example.saxtrainer.feature.reedlogbook

import com.example.saxtrainer.core.reed.Reed
import com.example.saxtrainer.core.reed.ReedCatalog
import com.example.saxtrainer.core.reed.ReedStatus
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * How the logbook says things: a reed in one line, a day, a time, how long.
 *
 * Plain functions of a time zone, so what the screen shows can be checked without one; the screens
 * pass the player's own.
 */
internal object ReedText {

    /** "No. 1" - plainly the number on the reed, not part of its make or model. */
    fun numberOf(reed: Reed): String = "No. ${reed.number}"

    /** "Reed No. 1". */
    fun titleOf(reed: Reed): String = "Reed ${numberOf(reed)}"

    /** "D'Addario Royal 2.5" - make, model and strength, always on one line of their own. */
    fun describe(reed: Reed): String = ReedCatalog.describe(reed)

    fun statusOf(status: ReedStatus): String = when (status) {
        ReedStatus.ACTIVE -> "In rotation"
        ReedStatus.INACTIVE -> "Put aside"
    }

    /** "Not played yet", "35 min played", "2 h 05 min played", "14 h played". */
    fun playedOf(duration: Duration): String {
        val minutes = duration.inWholeMinutes
        return when {
            duration == Duration.ZERO -> "Not played yet"
            minutes < 1 -> "Under a minute played"
            minutes < 60 -> "$minutes min played"
            minutes < 10 * 60 -> "${minutes / 60} h ${(minutes % 60).toInt().twoDigits()} min played"
            else -> "${minutes / 60} h played"
        }
    }

    /** "€4.50". */
    fun priceOf(cents: Long): String = "€${priceInputOf(cents)}"

    /** "4.50" - a price as the edit field shows it. */
    fun priceInputOf(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    /**
     * A typed price in cents: "4.50", "4,50", "4", "€ 4.5". Null for anything else, including
     * nothing typed - and for more than two decimals, which no price has.
     */
    fun parsePrice(text: String): Long? {
        val cleaned = text.trim().removePrefix("€").removeSuffix("€").trim().replace(',', '.')
        val match = PRICE.matchEntire(cleaned) ?: return null
        val euros = match.groupValues[1].toLongOrNull() ?: return null
        val cents = match.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        return euros * 100 + cents
    }

    private val PRICE = Regex("""(\d{1,5})(?:\.(\d{1,2}))?""")

    /** "Mon 5 Oct 2026". */
    fun dayOf(instant: Instant, zone: TimeZone): String = dayOf(instant.toLocalDateTime(zone).date)

    fun dayOf(date: LocalDate): String =
        "${WEEKDAYS[date.dayOfWeek.ordinal]} ${date.day} ${MONTHS[date.month.ordinal]} ${date.year}"

    /** "14:02". */
    fun timeOf(instant: Instant, zone: TimeZone): String = timeOf(instant.toLocalDateTime(zone).time)

    fun timeOf(time: LocalTime): String = "${time.hour.twoDigits()}:${time.minute.twoDigits()}"

    /** A typed length in whole minutes, "45"; null for anything else, nothing and nought included. */
    fun parseMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_SESSION_MINUTES }

    /** Longer than any one sitting - a slip of the thumb, not a session. */
    const val MAX_SESSION_MINUTES = 12 * 60

    /** "2026-10-05", for a file name. */
    fun isoDayOf(instant: Instant, zone: TimeZone): String = instant.toLocalDateTime(zone).date.toString()

    /** "42 min", "1 h 05 min", "under a minute". */
    fun lengthOf(duration: Duration): String {
        val minutes = duration.inWholeMinutes
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${(minutes % 60).toInt().twoDigits()} min"
        }
    }

    /** "0:42:13" - a running clock, to the second. */
    fun clockOf(duration: Duration): String {
        val seconds = duration.inWholeSeconds.coerceAtLeast(0)
        return "${seconds / 3600}:${((seconds / 60) % 60).toInt().twoDigits()}:${(seconds % 60).toInt().twoDigits()}"
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}

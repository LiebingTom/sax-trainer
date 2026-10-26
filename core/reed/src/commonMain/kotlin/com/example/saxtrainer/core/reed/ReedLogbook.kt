package com.example.saxtrainer.core.reed

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Where a reed is in its life: in rotation, or put aside.
 *
 * Two and no more. Put aside covers a reed resting and a reed that broke alike - one that is never
 * taken back simply stays put aside, kept with every session on it for the history. A reason can be
 * added beside the status later without either changing.
 */
enum class ReedStatus {
    /** In rotation: numbered, selectable, played. */
    ACTIVE,

    /** Put aside - resting, too soft, broken. Can be taken back into rotation. */
    INACTIVE,
}

/**
 * One physical reed.
 *
 * Two names for it, on purpose. [id] is the reed itself: given once, never changed, never shown, and
 * what every session points at. [number] is what is pencilled on it - "reed 2" - and only has to be
 * told apart from the other reeds in rotation, so once a reed is retired its number is free for the
 * next one. Two reeds in the history may well both have been number 1.
 *
 * Make and model are stable ids from [ReedCatalog], not display names, so a renamed product is
 * still the same product to every statistic; an id the catalog does not know is kept as it is.
 */
data class Reed(
    val id: String,
    val number: Int,
    val manufacturerId: String,
    val productId: String,
    /** A step of the product's own scale - "2.5", "3M", "2.25" - as the box says it. */
    val strength: String,
    val status: ReedStatus,
    val createdAt: Instant,
    /** When [status] last changed; null while the reed has never left its first status. */
    val statusChangedAt: Instant? = null,
    /**
     * What the reed cost, in euro cents - whole cents so nothing is lost to rounding when costs are
     * added up. Null when the player did not say.
     */
    val priceCents: Long? = null,
) {
    val isActive: Boolean get() = status == ReedStatus.ACTIVE
}

/**
 * One stretch of playing on one reed: from start to stop.
 *
 * Only the two moments are kept. The duration is worked out from them every time, so it can never
 * disagree with them - and while [end] is null the session is still running, timed from a [start]
 * that survives the app being closed.
 */
data class ReedSession(
    val id: String,
    /** The reed's [Reed.id] - never its number, which a later reed may carry. */
    val reedId: String,
    val start: Instant,
    val end: Instant? = null,
) {
    val isRunning: Boolean get() = end == null

    /** How long it lasted, or has lasted so far at [now] while it is running. */
    fun durationAt(now: Instant): Duration = (end ?: now) - start
}

/**
 * Every reed the player has had and every session played on them, plus the reed last chosen.
 *
 * One value, like a lesson book: every change is a function from a logbook to a logbook below, so
 * the rules - one number per reed in rotation, one session at a time - hold whoever makes the change,
 * and a repository only ever writes a whole logbook. A change the rules do not allow returns the
 * logbook unchanged rather than failing.
 */
data class ReedLogbook(
    val reeds: List<Reed> = emptyList(),
    val sessions: List<ReedSession> = emptyList(),
    /** The reed last chosen to play; see [effectiveSelection] for what is actually selected. */
    val selectedReedId: String? = null,
) {
    fun reedOf(id: String): Reed? = reeds.firstOrNull { it.id == id }

    /** The reeds in rotation, by number. */
    val activeReeds: List<Reed> get() = reeds.filter { it.isActive }.sortedBy { it.number }

    /** Every reed there has been, the newest first. */
    val reedsNewestFirst: List<Reed> get() = reeds.sortedWith(compareByDescending<Reed> { it.createdAt }.thenBy { it.id })

    /** The session being played right now, if there is one. There is never more than one. */
    val runningSession: ReedSession? get() = sessions.firstOrNull { it.isRunning }

    /**
     * How long a reed has been played in all, up to [now] for a session still running on it - what
     * a reed's wear is read off.
     */
    fun playedTime(reedId: String, now: Instant): Duration =
        sessions.filter { it.reedId == reedId }.fold(Duration.ZERO) { total, it -> total + it.durationAt(now) }

    /** How long every reed together has been played, up to [now] for a session still running. */
    fun totalPlayedTime(now: Instant): Duration =
        sessions.fold(Duration.ZERO) { total, it -> total + it.durationAt(now) }

    /** One reed's sessions, the newest first. */
    fun sessionsOf(reedId: String): List<ReedSession> =
        sessions.filter { it.reedId == reedId }.sortedByDescending { it.start }

    /** Whether [number] is free to give to a reed in rotation - other than [exceptReedId] itself. */
    fun isNumberFree(number: Int, exceptReedId: String? = null): Boolean =
        number >= 1 && reeds.none { it.isActive && it.number == number && it.id != exceptReedId }

    /**
     * The numbers a reed going into rotation could be given: every gap left by a retired reed, and
     * the one after the last. Never empty.
     */
    fun freeNumbers(): List<Int> = (1..activeReeds.size + 1).filter { isNumberFree(it) }

    /** What a new reed is numbered unless the player says otherwise: the lowest free number. */
    fun nextFreeNumber(): Int = freeNumbers().first()

    /**
     * The reed selected for the next session.
     *
     * The one being played while a session runs; otherwise the one last chosen, while it is still
     * in rotation; failing that the lowest-numbered reed in rotation; and with none, nothing.
     */
    fun effectiveSelection(): Reed? {
        runningSession?.let { running -> reedOf(running.reedId)?.let { return it } }
        val chosen = selectedReedId?.let(::reedOf)?.takeIf { it.isActive }
        return chosen ?: activeReeds.firstOrNull()
    }

    /** Whether a session can be started now: none running, and a reed selected. */
    val canStart: Boolean get() = runningSession == null && effectiveSelection() != null
}

/**
 * A new reed in rotation, numbered [number] and selected.
 *
 * A number another reed in rotation already has is refused: two reeds both called "2" is exactly
 * what the number is there to prevent. So is a second reed under an id already in use.
 */
fun ReedLogbook.plusReed(
    id: String,
    number: Int,
    manufacturerId: String,
    productId: String,
    strength: String,
    createdAt: Instant,
    priceCents: Long? = null,
): ReedLogbook {
    if (!isNumberFree(number) || reedOf(id) != null) return this
    val reed = Reed(
        id = id,
        number = number,
        manufacturerId = manufacturerId,
        productId = productId,
        strength = strength,
        status = ReedStatus.ACTIVE,
        createdAt = createdAt,
        priceCents = priceCents?.takeIf { it >= 0 },
    )
    return copy(reeds = reeds + reed, selectedReedId = id)
}

/**
 * The same reed, described differently: what make, model and strength it is, and what it cost.
 *
 * Its number is changed through [renumbered], which has a rule of its own to keep.
 */
fun ReedLogbook.withDetails(
    id: String,
    manufacturerId: String,
    productId: String,
    strength: String,
    // No default: leaving it out would quietly wipe a price already entered.
    priceCents: Long?,
): ReedLogbook = copy(
    reeds = reeds.map {
        if (it.id != id) {
            it
        } else {
            it.copy(
                manufacturerId = manufacturerId,
                productId = productId,
                strength = strength,
                priceCents = priceCents?.takeIf { cents -> cents >= 0 },
            )
        }
    },
)

/** Gives a reed in rotation another number, if no other reed in rotation has it. */
fun ReedLogbook.renumbered(id: String, number: Int): ReedLogbook {
    val reed = reedOf(id) ?: return this
    if (!reed.isActive || !isNumberFree(number, exceptReedId = id)) return this
    return copy(reeds = reeds.map { if (it.id == id) it.copy(number = number) else it })
}

/**
 * Moves a reed to another [status] at [now].
 *
 * - Leaving rotation frees its number, and stops a session running on it at [now]: a reed put away
 *   is not being played.
 * - Coming back into rotation takes [number] when it is given and free; otherwise its old number
 *   when that is still free, and failing that the lowest free one - never a number another reed in
 *   rotation has. Where its old number is taken, the screen asks which one before calling this; see
 *   [needsNumberToReturn].
 */
fun ReedLogbook.withStatus(id: String, status: ReedStatus, now: Instant, number: Int? = null): ReedLogbook {
    val reed = reedOf(id) ?: return this
    if (reed.status == status) return this

    val newNumber = when {
        status != ReedStatus.ACTIVE -> reed.number
        number != null && isNumberFree(number) -> number
        isNumberFree(reed.number) -> reed.number
        else -> nextFreeNumber()
    }
    val changed = reed.copy(status = status, number = newNumber, statusChangedAt = now)
    val stoppedSessions = if (status == ReedStatus.ACTIVE) sessions else sessions.map {
        if (it.reedId == id && it.isRunning) it.copy(end = now) else it
    }
    return copy(reeds = reeds.map { if (it.id == id) changed else it }, sessions = stoppedSessions)
}

/**
 * Whether a reed put aside cannot simply come back under its old number, because a reed in rotation
 * has it now - so the player has to say which number it is to carry instead.
 */
fun ReedLogbook.needsNumberToReturn(id: String): Boolean {
    val reed = reedOf(id) ?: return false
    return !reed.isActive && !isNumberFree(reed.number)
}

/**
 * Deletes a reed for good, and every session played on it with it. A session running on it goes
 * too, and it is no longer the selected reed.
 */
fun ReedLogbook.minusReed(id: String): ReedLogbook {
    if (reedOf(id) == null) return this
    return copy(
        reeds = reeds.filterNot { it.id == id },
        sessions = sessions.filterNot { it.reedId == id },
        selectedReedId = selectedReedId.takeUnless { it == id },
    )
}

/** Chooses the reed for the next session. Only a reed in rotation, and not while one is played. */
fun ReedLogbook.selected(id: String): ReedLogbook {
    if (runningSession != null || reedOf(id)?.isActive != true) return this
    return copy(selectedReedId = id)
}

/**
 * Starts a session on [reedId] at [now], and selects that reed.
 *
 * Only on a reed in rotation, and only when nothing is running: one mouthpiece, one reed, one
 * session at a time.
 */
fun ReedLogbook.started(sessionId: String, reedId: String, now: Instant): ReedLogbook {
    if (runningSession != null || reedOf(reedId)?.isActive != true) return this
    if (sessions.any { it.id == sessionId }) return this
    return copy(
        sessions = sessions + ReedSession(id = sessionId, reedId = reedId, start = now),
        selectedReedId = reedId,
    )
}

/**
 * Stops the running session at [now]. Nothing running, nothing to stop.
 *
 * A clock that has gone backwards since the start - the phone's time corrected - ends the session
 * where it began rather than before it.
 */
fun ReedLogbook.stopped(now: Instant): ReedLogbook {
    val running = runningSession ?: return this
    val end = maxOf(now, running.start)
    return copy(sessions = sessions.map { if (it.id == running.id) it.copy(end = end) else it })
}

/**
 * Whether [start] to [end] is free to enter as a session played earlier: it has a length, it is
 * over by [now], and no other session - on any reed, one being played included - shares any of it.
 * One mouthpiece, one reed: time entered twice would be counted twice.
 */
fun ReedLogbook.isFreeForSession(start: Instant, end: Instant, now: Instant): Boolean =
    start < end && end <= now && sessions.none { start < (it.end ?: now) && it.start < end }

/**
 * A session played earlier and entered by hand, on [reedId], from [start] to [end].
 *
 * On any reed in the logbook, one put aside too - it was played back then. Refused unless the time
 * is free (see [isFreeForSession]). The selection is left alone: entering the past chooses nothing
 * for the next session.
 */
fun ReedLogbook.plusSession(id: String, reedId: String, start: Instant, end: Instant, now: Instant): ReedLogbook {
    if (reedOf(reedId) == null || sessions.any { it.id == id }) return this
    if (!isFreeForSession(start, end, now)) return this
    return copy(sessions = sessions + ReedSession(id = id, reedId = reedId, start = start, end = end))
}

/** Deletes one session for good. */
fun ReedLogbook.minusSession(id: String): ReedLogbook = copy(sessions = sessions.filterNot { it.id == id })

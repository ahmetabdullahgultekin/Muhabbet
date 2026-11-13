package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * One of a user's bookmarks, together with the message it points at.
 *
 * [starredAt] and [starId] are carried because they are the sort key: the list is ordered by when
 * the user starred something, not by when it was sent, and [starId] breaks the tie between two
 * stars written in the same instant so the keyset cursor can never skip or repeat a row.
 */
data class StarredMessage(
    val starId: UUID,
    val starredAt: Instant,
    val message: Message
)

data class StarredMessagePage(
    val items: List<StarredMessage>,
    val nextCursor: StarredCursor?,
    val hasMore: Boolean
)

/**
 * Keyset position in a user's starred list: "everything starred strictly before this star".
 *
 * Replaces the old `offset` paging (#906), which computed `PageRequest.of(offset / limit, limit)` —
 * a `limit` of 0 divided by zero, and an offset that was not a multiple of the limit silently
 * returned the wrong page. A keyset cursor has neither failure, and it does not shift when the user
 * stars or unstars something between two page loads.
 *
 * The wire form is `<ISO-8601 instant>_<uuid>`. An ISO instant contains no underscore, so the split
 * is unambiguous.
 */
data class StarredCursor(val starredAt: Instant, val starId: UUID) {

    fun encode(): String = "$starredAt$SEPARATOR$starId"

    companion object {
        private const val SEPARATOR = '_'

        /** The cursor [raw] names, or null if it is not one this server produced. */
        fun parse(raw: String): StarredCursor? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 2) return null
            return try {
                StarredCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]))
            } catch (_: DateTimeParseException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/** What [StarPolicy] decided about one request to star one message. */
sealed interface StarDecision {
    data object Allowed : StarDecision

    /**
     * The caller may not learn that this message exists. Deliberately the same answer for "no such
     * id", "not a member of its conversation" and "somebody else's scheduled message that is not
     * due yet" — distinct answers would turn the endpoint into an oracle for message ids, which is
     * part of what #906 reported.
     */
    data object NotVisible : StarDecision

    /** The caller can see the message, but it has been deleted. */
    data object AlreadyDeleted : StarDecision

    /**
     * The caller can see the message, but it is not something a bookmark may hold: a view-once
     * message (a bookmark is a way to see it again, which is exactly what view-once forbids) or the
     * caller's own scheduled message that has not been sent yet.
     */
    data object NotStarrable : StarDecision
}

/**
 * Who may star what. Pure, so every branch is tested without a database.
 *
 * The rule mirrors what the caller could read through history: a bookmark is a pointer to
 * something you are allowed to see, and must never be a way to see something you are not.
 */
object StarPolicy {

    fun decide(message: Message?, callerIsMember: Boolean, callerId: UUID): StarDecision = when {
        message == null -> StarDecision.NotVisible
        !callerIsMember -> StarDecision.NotVisible
        message.isScheduled && message.senderId != callerId -> StarDecision.NotVisible
        message.isDeleted -> StarDecision.AlreadyDeleted
        message.viewOnce -> StarDecision.NotStarrable
        message.isScheduled -> StarDecision.NotStarrable
        else -> StarDecision.Allowed
    }
}

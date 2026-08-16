package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class GroupEvent(
    val id: UUID = UUID.randomUUID(),
    val conversationId: UUID,
    val createdBy: UUID,
    val title: String,
    val description: String? = null,
    val eventTime: Instant,
    val location: String? = null,
    val createdAt: Instant = Instant.now()
)

/**
 * An event together with how many members have answered GOING.
 *
 * The event list renders that number, so it is resolved once for the whole page rather than by
 * asking each event for its RSVPs in turn.
 */
data class GroupEventSummary(
    val event: GroupEvent,
    val goingCount: Int
)

enum class RsvpStatus {
    GOING, NOT_GOING, MAYBE
}

data class GroupEventRsvp(
    val eventId: UUID,
    val userId: UUID,
    val status: RsvpStatus = RsvpStatus.GOING,
    val respondedAt: Instant = Instant.now()
)

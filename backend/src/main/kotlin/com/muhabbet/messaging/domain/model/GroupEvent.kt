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

enum class RsvpStatus {
    GOING, NOT_GOING, MAYBE
}

data class GroupEventRsvp(
    val eventId: UUID,
    val userId: UUID,
    val status: RsvpStatus = RsvpStatus.GOING,
    val respondedAt: Instant = Instant.now()
)

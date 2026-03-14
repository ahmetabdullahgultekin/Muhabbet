package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class GroupCallParticipant(
    val callId: String,
    val userId: UUID,
    val joinedAt: Instant = Instant.now(),
    val leftAt: Instant? = null
)

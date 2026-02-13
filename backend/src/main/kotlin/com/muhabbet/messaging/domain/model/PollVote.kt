package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class PollVote(
    val id: UUID = UUID.randomUUID(),
    val messageId: UUID,
    val userId: UUID,
    val optionIndex: Int,
    val votedAt: Instant = Instant.now()
)

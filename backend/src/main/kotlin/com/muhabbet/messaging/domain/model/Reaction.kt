package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class Reaction(
    val id: UUID = UUID.randomUUID(),
    val messageId: UUID,
    val userId: UUID,
    val emoji: String,
    val createdAt: Instant = Instant.now()
)

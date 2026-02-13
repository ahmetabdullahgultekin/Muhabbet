package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class Status(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val content: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(86400)
)

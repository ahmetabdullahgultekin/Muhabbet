package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class BroadcastList(
    val id: UUID = UUID.randomUUID(),
    val ownerId: UUID,
    val name: String,
    val createdAt: Instant = Instant.now()
)

data class BroadcastListMember(
    val broadcastListId: UUID,
    val userId: UUID
)

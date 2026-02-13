package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class Bot(
    val id: UUID = UUID.randomUUID(),
    val ownerId: UUID,
    val userId: UUID,           // bot's user account ID
    val name: String,
    val description: String? = null,
    val apiToken: String,
    val webhookUrl: String? = null,
    val isActive: Boolean = true,
    val permissions: List<String> = listOf("SEND_MESSAGE", "READ_MESSAGE"),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class BotPermission {
    SEND_MESSAGE,
    READ_MESSAGE,
    MANAGE_GROUP,
    MANAGE_CHANNEL
}

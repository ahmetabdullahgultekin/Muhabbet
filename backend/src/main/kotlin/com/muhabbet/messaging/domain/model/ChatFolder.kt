package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A user-defined grouping of conversations ("Lists" in WhatsApp). Owned by one user; purely an
 * organizational overlay — assigning a conversation to a folder does not change the conversation.
 */
data class ChatFolder(
    val id: UUID = UUID.randomUUID(),
    val ownerId: UUID,
    val name: String,
    val position: Int = 0,
    val createdAt: Instant = Instant.now()
)

data class ChatFolderEntry(
    val folderId: UUID,
    val conversationId: UUID
)

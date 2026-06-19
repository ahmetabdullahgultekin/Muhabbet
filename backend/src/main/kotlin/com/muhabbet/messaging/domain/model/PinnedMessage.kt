package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A message pinned within a conversation so members can find it quickly (WhatsApp "pin in chat").
 * Distinct from pinning a whole conversation in the chat list (that lives on conversation_members).
 */
data class PinnedMessage(
    val conversationId: UUID,
    val messageId: UUID,
    val pinnedBy: UUID,
    val pinnedAt: Instant = Instant.now()
)

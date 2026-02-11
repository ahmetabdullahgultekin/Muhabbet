package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import java.util.UUID

interface CreateConversationUseCase {
    fun createConversation(
        type: ConversationType,
        creatorId: UUID,
        participantIds: List<UUID>,
        name: String? = null
    ): ConversationWithMembers
}

data class ConversationWithMembers(
    val conversation: Conversation,
    val members: List<ConversationMember>
)

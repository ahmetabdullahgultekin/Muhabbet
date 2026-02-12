package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface StarredMessageRepository {
    fun star(userId: UUID, messageId: UUID)
    fun unstar(userId: UUID, messageId: UUID)
    fun isStarred(userId: UUID, messageId: UUID): Boolean
    fun getStarredMessages(userId: UUID, limit: Int, offset: Int): List<Message>
    fun getStarredMessageIds(userId: UUID, messageIds: List<UUID>): Set<UUID>
}

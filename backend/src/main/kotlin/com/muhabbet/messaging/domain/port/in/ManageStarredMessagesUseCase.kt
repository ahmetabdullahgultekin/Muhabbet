package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface ManageStarredMessagesUseCase {
    /** Stars [messageId] for [userId]; refused unless [userId] can read that message. */
    fun star(userId: UUID, messageId: UUID)
    fun unstar(userId: UUID, messageId: UUID)

    /** The caller's starred messages that they can still read, newest star first. */
    fun getStarredMessages(userId: UUID, limit: Int, offset: Int): List<Message>
}

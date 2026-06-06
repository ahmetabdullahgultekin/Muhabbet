package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

/**
 * Searches messages on behalf of an authenticated user.
 *
 * Membership is enforced here (the service layer), NOT in the controller:
 * - [searchInConversation] verifies the requesting user is a member of the target conversation.
 * - [searchGlobal] only returns messages from conversations the requesting user belongs to.
 */
interface SearchMessagesUseCase {

    fun searchGlobal(requestingUserId: UUID, query: String, limit: Int, offset: Int): List<Message>

    fun searchInConversation(
        conversationId: UUID,
        requestingUserId: UUID,
        query: String,
        limit: Int,
        offset: Int
    ): List<Message>
}

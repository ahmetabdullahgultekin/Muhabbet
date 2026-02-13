package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import java.util.UUID

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation
    fun findById(id: UUID): Conversation?
    fun saveMember(member: ConversationMember): ConversationMember
    fun findMembersByConversationId(conversationId: UUID): List<ConversationMember>
    fun findMember(conversationId: UUID, userId: UUID): ConversationMember?
    fun findConversationsByUserId(userId: UUID): List<Conversation>
    fun findDirectConversation(userIdLow: UUID, userIdHigh: UUID): UUID?
    fun saveDirectLookup(userIdLow: UUID, userIdHigh: UUID, conversationId: UUID)
    fun updateLastReadAt(conversationId: UUID, userId: UUID, timestamp: java.time.Instant)

    // Group management
    fun removeMember(conversationId: UUID, userId: UUID)
    fun updateConversation(conversation: Conversation): Conversation
    fun updateMemberRole(conversationId: UUID, userId: UUID, role: com.muhabbet.messaging.domain.model.MemberRole)

    // Channel
    fun findByType(type: com.muhabbet.messaging.domain.model.ConversationType): List<Conversation>
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.DirectConversationLookupJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationMemberRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataDirectConversationLookupRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ConversationPersistenceAdapter(
    private val conversationRepo: SpringDataConversationRepository,
    private val memberRepo: SpringDataConversationMemberRepository,
    private val directLookupRepo: SpringDataDirectConversationLookupRepository
) : ConversationRepository {

    override fun save(conversation: Conversation): Conversation =
        conversationRepo.save(ConversationJpaEntity.fromDomain(conversation)).toDomain()

    override fun findById(id: UUID): Conversation? =
        conversationRepo.findById(id).orElse(null)?.toDomain()

    override fun saveMember(member: ConversationMember): ConversationMember =
        memberRepo.save(ConversationMemberJpaEntity.fromDomain(member)).toDomain()

    override fun findMembersByConversationId(conversationId: UUID): List<ConversationMember> =
        memberRepo.findByConversationId(conversationId).map { it.toDomain() }

    override fun findMember(conversationId: UUID, userId: UUID): ConversationMember? =
        memberRepo.findByConversationIdAndUserId(conversationId, userId)?.toDomain()

    override fun findConversationsByUserId(userId: UUID): List<Conversation> {
        val memberEntries = memberRepo.findByUserId(userId)
        val conversationIds = memberEntries.map { it.conversationId }
        return conversationRepo.findAllById(conversationIds).map { it.toDomain() }
    }

    override fun findDirectConversation(userIdLow: UUID, userIdHigh: UUID): UUID? =
        directLookupRepo.findByUserIdLowAndUserIdHigh(userIdLow, userIdHigh)?.conversationId

    override fun saveDirectLookup(userIdLow: UUID, userIdHigh: UUID, conversationId: UUID) {
        directLookupRepo.save(
            DirectConversationLookupJpaEntity(
                userIdLow = userIdLow,
                userIdHigh = userIdHigh,
                conversationId = conversationId
            )
        )
    }

    override fun updateLastReadAt(conversationId: UUID, userId: UUID, timestamp: Instant) {
        memberRepo.updateLastReadAt(conversationId, userId, timestamp)
    }

    override fun removeMember(conversationId: UUID, userId: UUID) {
        memberRepo.deleteByConversationIdAndUserId(conversationId, userId)
    }

    override fun updateConversation(conversation: Conversation): Conversation {
        val entity = conversationRepo.findById(conversation.id).orElse(null) ?: return conversation
        entity.name = conversation.name
        entity.avatarUrl = conversation.avatarUrl
        entity.description = conversation.description
        entity.updatedAt = conversation.updatedAt
        entity.disappearAfterSeconds = conversation.disappearAfterSeconds
        return conversationRepo.save(entity).toDomain()
    }

    override fun updateMemberRole(conversationId: UUID, userId: UUID, role: com.muhabbet.messaging.domain.model.MemberRole) {
        memberRepo.updateRole(conversationId, userId, role)
    }

    override fun findByType(type: ConversationType): List<Conversation> =
        conversationRepo.findAll()
            .filter { it.type == type }
            .map { it.toDomain() }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ChannelInfo
import com.muhabbet.messaging.domain.port.`in`.ManageChannelUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ChannelService(
    private val conversationRepository: ConversationRepository
) : ManageChannelUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun subscribe(channelId: UUID, userId: UUID) {
        val conversation = conversationRepository.findById(channelId)
            ?: throw BusinessException(ErrorCode.CHANNEL_NOT_FOUND)

        if (conversation.type != ConversationType.CHANNEL) {
            throw BusinessException(ErrorCode.CHANNEL_NOT_A_CHANNEL)
        }

        // Check if already subscribed
        val existing = conversationRepository.findMember(channelId, userId)
        if (existing != null) {
            return
        }

        conversationRepository.saveMember(
            ConversationMember(
                conversationId = channelId,
                userId = userId,
                role = MemberRole.MEMBER
            )
        )
        log.info("User {} subscribed to channel {}", userId, channelId)
    }

    @Transactional
    override fun unsubscribe(channelId: UUID, userId: UUID) {
        val member = conversationRepository.findMember(channelId, userId)
        if (member != null && member.role == MemberRole.MEMBER) {
            conversationRepository.removeMember(channelId, userId)
            log.info("User {} unsubscribed from channel {}", userId, channelId)
        }
    }

    @Transactional(readOnly = true)
    override fun listChannels(userId: UUID): List<ChannelInfo> {
        // findByType is unpaginated: it returns every channel in the system. That is tolerable only
        // while the member lookup is a single batched query — resolving members per channel made the
        // cost of one request grow with the channel count (N+1). Do not reintroduce a per-channel
        // query here; if the channel list itself ever grows large, paginate findByType instead.
        val channels = conversationRepository.findByType(ConversationType.CHANNEL)
        if (channels.isEmpty()) return emptyList()

        val membersByChannel = conversationRepository.findMembersByConversationIds(channels.map { it.id })

        return channels.map { conv ->
            val members = membersByChannel[conv.id] ?: emptyList()
            ChannelInfo(
                id = conv.id,
                name = conv.name ?: "",
                description = conv.description,
                subscriberCount = members.size,
                isSubscribed = members.any { it.userId == userId },
                createdAt = conv.createdAt.toString()
            )
        }
    }
}

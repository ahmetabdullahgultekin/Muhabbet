package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationMemberRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationRepository
import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberJpaEntity
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ChannelInfoResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/channels")
class ChannelController(
    private val conversationRepo: SpringDataConversationRepository,
    private val memberRepo: SpringDataConversationMemberRepository
) {

    @PostMapping("/{channelId}/subscribe")
    @Transactional
    fun subscribe(@PathVariable channelId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val conv = conversationRepo.findById(channelId).orElse(null)
            ?: return ApiResponseBuilder.notFound("Channel not found")

        if (conv.type != ConversationType.CHANNEL) {
            return ApiResponseBuilder.badRequest("Not a channel")
        }

        // Check if already subscribed
        val existing = memberRepo.findByConversationIdAndUserId(channelId, userId)
        if (existing != null) {
            return ApiResponseBuilder.ok(Unit)
        }

        memberRepo.save(
            ConversationMemberJpaEntity(
                conversationId = channelId,
                userId = userId,
                role = MemberRole.MEMBER,
                joinedAt = Instant.now()
            )
        )
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{channelId}/subscribe")
    @Transactional
    fun unsubscribe(@PathVariable channelId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val member = memberRepo.findByConversationIdAndUserId(channelId, userId)
        if (member != null && member.role == MemberRole.MEMBER) {
            memberRepo.delete(member)
        }
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping
    fun listChannels(): ResponseEntity<ApiResponse<List<ChannelInfoResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val channels = conversationRepo.findAll()
            .filter { it.type == ConversationType.CHANNEL }
            .map { conv ->
                val memberCount = memberRepo.findByConversationId(conv.id).size
                val isSubscribed = memberRepo.findByConversationIdAndUserId(conv.id, userId) != null
                ChannelInfoResponse(
                    id = conv.id.toString(),
                    name = conv.name ?: "",
                    description = conv.description,
                    subscriberCount = memberCount,
                    isSubscribed = isSubscribed,
                    createdAt = conv.createdAt.toString()
                )
            }
        return ApiResponseBuilder.ok(channels)
    }
}

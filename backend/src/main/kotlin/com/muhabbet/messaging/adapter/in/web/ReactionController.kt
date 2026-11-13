package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MessageReactionsResponse
import com.muhabbet.shared.dto.ReactionRequest
import com.muhabbet.shared.dto.ReactionResponse
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/messages")
class ReactionController(
    private val manageReactionUseCase: ManageReactionUseCase,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val sessionManager: WebSocketSessionManager
) {

    /**
     * Answers with the message's reactions as the caller now sees them, and broadcasts the
     * resulting count, so neither the caller nor any other member does arithmetic on a frame that
     * may arrive twice (#783).
     */
    @PostMapping("/{messageId}/reactions")
    fun addReaction(
        @PathVariable messageId: UUID,
        @RequestBody request: ReactionRequest
    ): ResponseEntity<ApiResponse<MessageReactionsResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val groups = manageReactionUseCase.addReaction(messageId, userId, request.emoji)
        broadcastReaction(messageId, userId, request.emoji, "add", groups)
        return ApiResponseBuilder.ok(groups.toMessageReactions(messageId, userId))
    }

    @DeleteMapping("/{messageId}/reactions/{emoji}")
    fun removeReaction(
        @PathVariable messageId: UUID,
        @PathVariable emoji: String
    ): ResponseEntity<ApiResponse<MessageReactionsResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val groups = manageReactionUseCase.removeReaction(messageId, userId, emoji)
        broadcastReaction(messageId, userId, emoji, "remove", groups)
        return ApiResponseBuilder.ok(groups.toMessageReactions(messageId, userId))
    }

    private fun broadcastReaction(
        messageId: UUID,
        userId: UUID,
        emoji: String,
        action: String,
        groups: List<ReactionGroup>
    ) {
        val message = messageRepository.findById(messageId) ?: return
        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        val wsMessage = wsJson.encodeToString(
            WsMessage.serializer(),
            WsMessage.MessageReaction(
                messageId = messageId.toString(),
                conversationId = message.conversationId.toString(),
                userId = userId.toString(),
                emoji = emoji,
                action = action,
                count = groups.firstOrNull { it.emoji == emoji }?.count ?: 0
            )
        )
        members.forEach { member -> sessionManager.sendToUser(member.userId, wsMessage) }
    }

    @GetMapping("/{messageId}/reactions")
    fun getReactions(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<List<ReactionResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val reactions = manageReactionUseCase.getReactions(messageId, userId)
        return ApiResponseBuilder.ok(reactions.map { it.toDto() })
    }

    private fun List<ReactionGroup>.toMessageReactions(messageId: UUID, viewerId: UUID) =
        MessageReactionsResponse(
            messageId = messageId.toString(),
            reactions = associate { it.emoji to it.count },
            myReactions = filter { viewerId in it.userIds }.map { it.emoji }.toSet()
        )

    private fun ReactionGroup.toDto() = ReactionResponse(
        emoji = emoji,
        count = count,
        userIds = userIds.map { it.toString() }
    )
}
